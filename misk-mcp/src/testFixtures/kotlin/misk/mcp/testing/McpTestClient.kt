package misk.mcp.testing

import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import misk.mcp.protocol.CallToolRequest
import misk.mcp.protocol.CallToolRequestParams
import misk.mcp.protocol.CallToolResult
import misk.mcp.protocol.ClientCapabilities
import misk.mcp.protocol.CompleteRequest
import misk.mcp.protocol.CompleteRequestParams
import misk.mcp.protocol.CompleteResult
import misk.mcp.protocol.DiscoverResult
import misk.mcp.protocol.ElicitResult
import misk.mcp.protocol.GetPromptRequest
import misk.mcp.protocol.GetPromptRequestParams
import misk.mcp.protocol.GetPromptResult
import misk.mcp.protocol.Implementation
import misk.mcp.protocol.InitializeRequestParams
import misk.mcp.protocol.InitializeResult
import misk.mcp.protocol.InputRequest
import misk.mcp.protocol.InputRequiredResult
import misk.mcp.protocol.JSONRPCError
import misk.mcp.protocol.JSONRPCMessage
import misk.mcp.protocol.JSONRPCNotification
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.LATEST_PROTOCOL_VERSION
import misk.mcp.protocol.ListPromptsRequest
import misk.mcp.protocol.ListPromptsResult
import misk.mcp.protocol.ListResourceTemplatesRequest
import misk.mcp.protocol.ListResourceTemplatesResult
import misk.mcp.protocol.ListResourcesRequest
import misk.mcp.protocol.ListResourcesResult
import misk.mcp.protocol.ListToolsRequest
import misk.mcp.protocol.ListToolsResult
import misk.mcp.protocol.McpException
import misk.mcp.protocol.McpHeaders
import misk.mcp.protocol.McpMetaKeys
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.PaginatedRequestParams
import misk.mcp.protocol.ReadResourceRequest
import misk.mcp.protocol.ReadResourceRequestParams
import misk.mcp.protocol.ReadResourceResult
import misk.mcp.protocol.RequestId
import misk.mcp.protocol.RequestMeta
import misk.mcp.protocol.SUPPORTED_LEGACY_PROTOCOL_VERSIONS
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * An MCP client for tests that talks Streamable HTTP to a misk MCP endpoint.
 *
 * [protocolVersion] picks the era. A modern version (the default) sends the version, [clientInfo] and [capabilities] in
 * every request's `_meta`. A legacy version opens with `initialize` before the first request. Every request sets the
 * `MCP-Protocol-Version`, `Mcp-Method` and `Mcp-Name` headers.
 *
 * Methods throw [McpException] when the server returns a JSON-RPC error.
 */
class McpTestClient
@JvmOverloads
constructor(
  private val okHttpClient: OkHttpClient,
  private val url: HttpUrl,
  val protocolVersion: String = LATEST_PROTOCOL_VERSION,
  val capabilities: ClientCapabilities = ClientCapabilities(),
  val clientInfo: Implementation = Implementation(name = "misk-mcp-test", version = "1.0"),
  private val inputHandler: InputHandler? = null,
) {
  /** Answers the input requests of an `input_required` result, so the client can retry the request. */
  fun interface InputHandler {
    suspend fun respond(key: String, request: InputRequest): ElicitResult
  }

  private val nextId = AtomicLong(1)
  private val initializeMutex = Mutex()
  private var initializeResult: InitializeResult? = null

  val isLegacy: Boolean
    get() = protocolVersion in SUPPORTED_LEGACY_PROTOCOL_VERSIONS

  suspend fun discover(): DiscoverResult = request(McpMethod.SERVER_DISCOVER, null, DiscoverResult.serializer())

  /** Opens a legacy session. Other methods call this on first use, so tests rarely need to. */
  suspend fun initialize(): InitializeResult =
    initializeMutex.withLock {
      initializeResult?.let {
        return it
      }
      val params =
        InitializeRequestParams(protocolVersion = protocolVersion, capabilities = capabilities, clientInfo = clientInfo)
      val result =
        decodeResult(
          InitializeResult.serializer(),
          send(McpMethod.INITIALIZE, encode(InitializeRequestParams.serializer(), params)),
        )
      send(JSONRPCNotification(method = McpMethod.NOTIFICATIONS_INITIALIZED))
      initializeResult = result
      result
    }

  /** The server's name and version, from `initialize` for a legacy client and from `server/discover` otherwise. */
  suspend fun serverInfo(): Implementation =
    if (isLegacy) {
      initialize().serverInfo
    } else {
      val serverInfo = discover().meta?.get(McpMetaKeys.SERVER_INFO) ?: error("server/discover returned no serverInfo")
      json.decodeFromJsonElement(Implementation.serializer(), serverInfo)
    }

  suspend fun ping() {
    request(McpMethod.PING, null)
  }

  suspend fun listTools(request: ListToolsRequest = ListToolsRequest()): ListToolsResult =
    request(McpMethod.TOOLS_LIST, request.params?.let(::encodePaginated), ListToolsResult.serializer())

  suspend fun callTool(
    name: String,
    arguments: Map<String, Any?> = emptyMap(),
    meta: Map<String, Any?> = emptyMap(),
  ): CallToolResult =
    callTool(
      CallToolRequest(
        CallToolRequestParams(
          name = name,
          arguments = arguments.toJsonObject(),
          meta = meta.takeIf { it.isNotEmpty() }?.let { RequestMeta(it.toJsonObject()) },
        )
      )
    )

  suspend fun callTool(request: CallToolRequest): CallToolResult =
    request(
      McpMethod.TOOLS_CALL,
      encode(CallToolRequestParams.serializer(), request.params),
      CallToolResult.serializer(),
    )

  suspend fun listPrompts(request: ListPromptsRequest = ListPromptsRequest()): ListPromptsResult =
    request(McpMethod.PROMPTS_LIST, request.params?.let(::encodePaginated), ListPromptsResult.serializer())

  suspend fun getPrompt(request: GetPromptRequest): GetPromptResult =
    request(
      McpMethod.PROMPTS_GET,
      encode(GetPromptRequestParams.serializer(), request.params),
      GetPromptResult.serializer(),
    )

  suspend fun listResources(request: ListResourcesRequest = ListResourcesRequest()): ListResourcesResult =
    request(McpMethod.RESOURCES_LIST, request.params?.let(::encodePaginated), ListResourcesResult.serializer())

  suspend fun listResourceTemplates(
    request: ListResourceTemplatesRequest = ListResourceTemplatesRequest()
  ): ListResourceTemplatesResult =
    request(
      McpMethod.RESOURCES_TEMPLATES_LIST,
      request.params?.let(::encodePaginated),
      ListResourceTemplatesResult.serializer(),
    )

  suspend fun readResource(request: ReadResourceRequest): ReadResourceResult =
    request(
      McpMethod.RESOURCES_READ,
      encode(ReadResourceRequestParams.serializer(), request.params),
      ReadResourceResult.serializer(),
    )

  suspend fun complete(request: CompleteRequest): CompleteResult =
    request(
      McpMethod.COMPLETION_COMPLETE,
      encode(CompleteRequestParams.serializer(), request.params),
      CompleteResult.serializer(),
    )

  /**
   * Sends a request and returns its raw result. With an [InputHandler], an `input_required` result is answered and the
   * request retried until it completes.
   */
  suspend fun request(method: String, params: JsonObject?): JsonObject {
    if (isLegacy && method != McpMethod.INITIALIZE) initialize()
    var roundParams = params
    repeat(MAX_INPUT_ROUNDS) {
      val result = resultOf(method, send(method, roundParams))
      if (!result.isInputRequired() || inputHandler == null) return result
      val inputRequired = json.decodeFromJsonElement(InputRequiredResult.serializer(), result)
      val responses =
        inputRequired.inputRequests.orEmpty().mapValues { (key, request) ->
          json.encodeToJsonElement(ElicitResult.serializer(), inputHandler.respond(key, request))
        }
      roundParams =
        JsonObject(
          params.orEmpty() +
            buildMap {
              if (responses.isNotEmpty()) put("inputResponses", JsonObject(responses))
              inputRequired.requestState?.let { put("requestState", JsonPrimitive(it)) }
            }
        )
    }
    throw IllegalStateException("$method still requires input after $MAX_INPUT_ROUNDS rounds")
  }

  private suspend fun <T> request(method: String, params: JsonObject?, deserializer: KSerializer<T>): T {
    val result = request(method, params)
    check(!result.isInputRequired()) { "$method requires input: pass an InputHandler to McpTestClient" }
    return json.decodeFromJsonElement(deserializer, result)
  }

  private fun resultOf(method: String, response: McpTestResponse): JsonObject =
    when (val reply = response.reply) {
      is JSONRPCResponse -> reply.result
      is JSONRPCError -> throw McpException(reply.error.code, reply.error.message, reply.error.data)
      else -> throw IllegalStateException("No JSON-RPC response to $method: HTTP ${response.code}")
    }

  private fun JsonObject.isInputRequired() = (this["resultType"] as? JsonPrimitive)?.contentOrNull == "input_required"

  private suspend fun send(method: String, params: JsonObject?): McpTestResponse = send(newRequest(method, params))

  /** Builds a request with the next id and, for a modern client, the protocol `_meta`, for use with [send]. */
  fun newRequest(method: String, params: JsonObject? = null): JSONRPCRequest =
    JSONRPCRequest(id = RequestId(nextId.getAndIncrement()), method = method, params = withMeta(method, params))

  /** Sends [message] as is, plus the Streamable HTTP headers derived from it and any [headers] overrides. */
  suspend fun send(message: JSONRPCMessage, headers: Map<String, String?> = emptyMap()): McpTestResponse {
    val requestHeaders = Headers.Builder()
    requestHeaders["Accept"] = "application/json, text/event-stream"
    standardHeaders(message).plus(headers).forEach { (name, value) ->
      if (value != null) requestHeaders[name] = value else requestHeaders.removeAll(name)
    }
    val body = json.encodeToString(JSONRPCMessage.serializer(), message).toRequestBody(JSON_MEDIA_TYPE)
    val request = Request.Builder().url(url).headers(requestHeaders.build()).post(body).build()
    return withContext(Dispatchers.IO) {
      okHttpClient.newCall(request).execute().use { response ->
        val text = response.body.string()
        val messages =
          when {
            text.isBlank() -> emptyList()
            response.header("Content-Type").orEmpty().startsWith("text/event-stream") ->
              parseSse(text).map { json.decodeFromString(JSONRPCMessage.serializer(), it) }
            else -> listOfNotNull(runCatching { json.decodeFromString(JSONRPCMessage.serializer(), text) }.getOrNull())
          }
        McpTestResponse(response.code, response.headers, text, messages, (message as? JSONRPCRequest)?.id)
      }
    }
  }

  private fun withMeta(method: String, params: JsonObject?): JsonObject? {
    if (isLegacy || method == McpMethod.INITIALIZE) return params
    val meta = (params?.get("_meta") as? JsonObject).orEmpty()
    val protocolMeta =
      mapOf(
        McpMetaKeys.PROTOCOL_VERSION to JsonPrimitive(protocolVersion),
        McpMetaKeys.CLIENT_INFO to encode(Implementation.serializer(), clientInfo),
        McpMetaKeys.CLIENT_CAPABILITIES to encode(ClientCapabilities.serializer(), capabilities),
      )
    return JsonObject(params.orEmpty() + ("_meta" to JsonObject(protocolMeta + meta)))
  }

  private fun standardHeaders(message: JSONRPCMessage): Map<String, String> = buildMap {
    val method =
      when (message) {
        is JSONRPCRequest -> message.method
        is JSONRPCNotification -> message.method
        else -> null
      }
    if (method != McpMethod.INITIALIZE)
      put(McpHeaders.PROTOCOL_VERSION, initializeResult?.protocolVersion ?: protocolVersion)
    if (method != null) put(McpHeaders.METHOD, method)
    val params = (message as? JSONRPCRequest)?.params
    val name =
      when (method) {
        McpMethod.TOOLS_CALL,
        McpMethod.PROMPTS_GET -> params?.get("name")
        McpMethod.RESOURCES_READ -> params?.get("uri")
        else -> null
      }
    (name as? JsonPrimitive)?.contentOrNull?.let { put(McpHeaders.NAME, encodeHeaderValue(it)) }
  }

  private fun <T> decodeResult(deserializer: KSerializer<T>, response: McpTestResponse): T =
    when (val reply = response.reply) {
      is JSONRPCResponse -> json.decodeFromJsonElement(deserializer, reply.result)
      is JSONRPCError -> throw McpException(reply.error.code, reply.error.message, reply.error.data)
      else -> throw IllegalStateException("No JSON-RPC response: HTTP ${response.code}")
    }

  private fun encodePaginated(params: PaginatedRequestParams) = encode(PaginatedRequestParams.serializer(), params)

  private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
    json.encodeToJsonElement(serializer, value).jsonObject

  companion object {
    private const val MAX_INPUT_ROUNDS = 10
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      explicitNulls = false
      isLenient = true
    }

    /** Encodes a header value with the `=?base64?…?=` sentinel when it isn't safe to send as plain ASCII. */
    fun encodeHeaderValue(value: String): String =
      if (value.all { it in ' '..'~' } && value == value.trim()) value
      else "=?base64?${Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))}?="

    private fun parseSse(text: String): List<String> {
      val events = mutableListOf<String>()
      val data = StringBuilder()
      for (line in text.lines()) {
        when {
          line.isEmpty() -> {
            if (data.isNotEmpty()) events += data.toString()
            data.clear()
          }
          line.startsWith("data:") -> {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.removePrefix("data:").removePrefix(" "))
          }
        }
      }
      if (data.isNotEmpty()) events += data.toString()
      return events
    }
  }
}

/** The HTTP response to one [McpTestClient.send]. [reply] is the response to the request, if there was one. */
data class McpTestResponse(
  val code: Int,
  val headers: Headers,
  val body: String,
  val messages: List<JSONRPCMessage>,
  private val requestId: RequestId?,
) {
  val reply: JSONRPCMessage?
    get() =
      messages.firstOrNull {
        (it is JSONRPCResponse && it.id == requestId) || (it is JSONRPCError && it.id == requestId)
      } ?: messages.firstOrNull { it is JSONRPCError && it.id == null }

  val notifications: List<JSONRPCNotification>
    get() = messages.filterIsInstance<JSONRPCNotification>()
}

private fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { (_, value) -> value.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement =
  when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Enum<*> -> JsonPrimitive(name)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> throw IllegalArgumentException("Can't convert ${this::class} to JSON")
  }
