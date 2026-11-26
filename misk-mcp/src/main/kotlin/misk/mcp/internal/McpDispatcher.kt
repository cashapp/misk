package misk.mcp.internal

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import misk.annotation.ExperimentalMiskApi
import misk.logging.getLogger
import misk.mcp.McpJson
import misk.mcp.McpMetrics
import misk.mcp.MiskMcpServer
import misk.mcp.protocol.CallToolRequest
import misk.mcp.protocol.CallToolRequestParams
import misk.mcp.protocol.CallToolResult
import misk.mcp.protocol.DiscoverResult
import misk.mcp.protocol.EmptyJsonObject
import misk.mcp.protocol.GetPromptRequest
import misk.mcp.protocol.GetPromptRequestParams
import misk.mcp.protocol.GetPromptResult
import misk.mcp.protocol.Implementation
import misk.mcp.protocol.InitializeRequestParams
import misk.mcp.protocol.InitializeResult
import misk.mcp.protocol.JSONRPCError
import misk.mcp.protocol.JSONRPCMessage
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.LATEST_LEGACY_PROTOCOL_VERSION
import misk.mcp.protocol.ListPromptsResult
import misk.mcp.protocol.ListResourceTemplatesResult
import misk.mcp.protocol.ListResourcesResult
import misk.mcp.protocol.ListToolsResult
import misk.mcp.protocol.McpException
import misk.mcp.protocol.McpMetaKeys
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.ReadResourceRequest
import misk.mcp.protocol.ReadResourceRequestParams
import misk.mcp.protocol.ReadResourceResult
import misk.mcp.protocol.RequestMeta
import misk.mcp.protocol.SUPPORTED_LEGACY_PROTOCOL_VERSIONS
import misk.mcp.protocol.SUPPORTED_PROTOCOL_VERSIONS
import misk.mcp.protocol.TextContent

/**
 * The protocol era of one request. Modern requests carry their version in `_meta`; legacy ones rely on `initialize`.
 */
internal enum class McpEra {
  Modern,
  Legacy,
}

/** Routes JSON-RPC requests to the server's tools, resources and prompts, inline on the caller's coroutine. */
@OptIn(ExperimentalMiskApi::class)
internal class McpDispatcher(private val server: MiskMcpServer) {

  suspend fun respond(request: JSONRPCRequest): JSONRPCMessage =
    try {
      JSONRPCResponse(id = request.id, result = dispatch(request))
    } catch (e: CancellationException) {
      throw e
    } catch (e: McpException) {
      JSONRPCError(id = request.id, error = RPCError(e.code, e.message ?: "MCP error ${e.code}", e.data))
    } catch (e: Exception) {
      logger.error(e) { "Error handling MCP request ${request.method}" }
      JSONRPCError(id = request.id, error = RPCError(RPCError.ErrorCode.INTERNAL_ERROR, e.message ?: "Internal error"))
    }

  private suspend fun dispatch(request: JSONRPCRequest): JsonObject {
    val meta = (request.params?.get("_meta") as? JsonObject)?.let(::RequestMeta)
    return when (eraOf(request, meta)) {
      McpEra.Modern -> {
        validateModern(meta)
        decorateModern(request.method, dispatchModern(request))
      }
      McpEra.Legacy -> dispatchLegacy(request)
    }
  }

  private fun eraOf(request: JSONRPCRequest, meta: RequestMeta?): McpEra =
    when {
      request.method == McpMethod.INITIALIZE -> McpEra.Legacy
      meta?.protocolVersion != null || request.method == McpMethod.SERVER_DISCOVER -> McpEra.Modern
      else -> McpEra.Legacy
    }

  private fun validateModern(meta: RequestMeta?) {
    val requested =
      meta?.protocolVersion ?: throw invalidParams("Missing required _meta field ${McpMetaKeys.PROTOCOL_VERSION}")
    if (meta[McpMetaKeys.CLIENT_CAPABILITIES] !is JsonObject) {
      throw invalidParams("Missing required _meta field ${McpMetaKeys.CLIENT_CAPABILITIES}")
    }
    if (requested !in SUPPORTED_PROTOCOL_VERSIONS) {
      throw McpException(
        code = RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
        message = "Unsupported protocol version",
        data =
          buildJsonObject {
            put("supported", JsonArray(SUPPORTED_PROTOCOL_VERSIONS.map(::JsonPrimitive)))
            put("requested", requested)
          },
      )
    }
  }

  private suspend fun dispatchModern(request: JSONRPCRequest): JsonObject =
    when (request.method) {
      McpMethod.SERVER_DISCOVER ->
        encode(
          DiscoverResult.serializer(),
          DiscoverResult(
            supportedVersions = SUPPORTED_PROTOCOL_VERSIONS,
            capabilities = server.capabilities,
            instructions = server.instructions,
          ),
        )
      else -> dispatchFeature(request, McpEra.Modern) ?: throw methodNotFound(request.method)
    }

  private suspend fun dispatchLegacy(request: JSONRPCRequest): JsonObject =
    when (request.method) {
      McpMethod.INITIALIZE -> {
        val params = decodeParams(request, InitializeRequestParams.serializer())
        val version =
          params.protocolVersion.takeIf { it in SUPPORTED_LEGACY_PROTOCOL_VERSIONS } ?: LATEST_LEGACY_PROTOCOL_VERSION
        encode(
          InitializeResult.serializer(),
          InitializeResult(
            protocolVersion = version,
            capabilities = server.capabilities,
            serverInfo = server.serverInfo,
            instructions = server.instructions,
          ),
        )
      }
      McpMethod.PING -> EmptyJsonObject
      else -> dispatchFeature(request, McpEra.Legacy) ?: throw methodNotFound(request.method)
    }

  /** Handles the methods both eras share, or returns null when the method is unknown or its capability is off. */
  private suspend fun dispatchFeature(request: JSONRPCRequest, era: McpEra): JsonObject? {
    if (!capabilityEnabled(request.method)) return null
    return when (request.method) {
      McpMethod.TOOLS_LIST ->
        encode(ListToolsResult.serializer(), ListToolsResult(tools = server.tools.values.map { it.definition }))
      McpMethod.TOOLS_CALL ->
        encode(
          CallToolResult.serializer(),
          callTool(CallToolRequest(decodeParams(request, CallToolRequestParams.serializer()))),
        )
      McpMethod.PROMPTS_LIST ->
        encode(ListPromptsResult.serializer(), ListPromptsResult(prompts = server.prompts.values.map { it.definition }))
      McpMethod.PROMPTS_GET ->
        encode(
          GetPromptResult.serializer(),
          getPrompt(GetPromptRequest(decodeParams(request, GetPromptRequestParams.serializer()))),
        )
      McpMethod.RESOURCES_LIST ->
        encode(
          ListResourcesResult.serializer(),
          ListResourcesResult(resources = server.resources.values.map { it.definition }),
        )
      McpMethod.RESOURCES_TEMPLATES_LIST ->
        encode(
          ListResourceTemplatesResult.serializer(),
          ListResourceTemplatesResult(resourceTemplates = server.resourceTemplates.map { it.definition }),
        )
      McpMethod.RESOURCES_READ ->
        encode(
          ReadResourceResult.serializer(),
          readResource(ReadResourceRequest(decodeParams(request, ReadResourceRequestParams.serializer())), era),
        )
      else -> null
    }
  }

  private fun capabilityEnabled(method: String): Boolean {
    val capabilities = server.capabilities
    return when (method) {
      McpMethod.TOOLS_LIST,
      McpMethod.TOOLS_CALL -> capabilities.tools != null
      McpMethod.PROMPTS_LIST,
      McpMethod.PROMPTS_GET -> capabilities.prompts != null
      McpMethod.RESOURCES_LIST,
      McpMethod.RESOURCES_TEMPLATES_LIST,
      McpMethod.RESOURCES_READ -> capabilities.resources != null
      else -> false
    }
  }

  private suspend fun callTool(request: CallToolRequest): CallToolResult {
    val tool = server.tools[request.name]?.handler ?: throw invalidParams("Unknown tool: ${request.name}")
    val mark = TimeSource.Monotonic.markNow()
    var outcome = McpMetrics.ToolCallOutcome.Exception
    try {
      return tool.handler(request).also { result ->
        outcome = if (result.isError == true) McpMetrics.ToolCallOutcome.Error else McpMetrics.ToolCallOutcome.Success
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: McpException) {
      throw e
    } catch (e: Exception) {
      logger.error(e) { "Error executing tool ${request.name}" }
      return CallToolResult(
        content = listOf(TextContent("Error executing tool ${request.name}: ${e.message}")),
        isError = true,
      )
    } finally {
      server.mcpMetrics.mcpToolHandlerLatency(mark.elapsedNow(), server.name, server.version, request.name, outcome)
    }
  }

  private suspend fun getPrompt(request: GetPromptRequest): GetPromptResult {
    val prompt = server.prompts[request.name]?.handler ?: throw invalidParams("Unknown prompt: ${request.name}")
    val missing = prompt.arguments.filter { it.required == true && request.arguments?.get(it.name) == null }
    if (missing.isNotEmpty()) {
      throw invalidParams("Missing required arguments for prompt ${request.name}: ${missing.joinToString { it.name }}")
    }
    return prompt.handler(request)
  }

  private suspend fun readResource(request: ReadResourceRequest, era: McpEra): ReadResourceResult {
    server.resources[request.uri]?.let {
      return it.handler.handler(request)
    }

    val (template, match) =
      server.resourceTemplates
        .mapNotNull { template -> template.uriTemplate.match(request.uri)?.let { template to it } }
        .maxByOrNull { (_, match) -> match }
        ?: throw McpException(
          code =
            when (era) {
              McpEra.Modern -> RPCError.ErrorCode.INVALID_PARAMS
              McpEra.Legacy -> RPCError.ErrorCode.RESOURCE_NOT_FOUND
            },
          message = "Resource not found",
          data = buildJsonObject { put("uri", request.uri) },
        )
    return template.handler.handler(request, match.variables)
  }

  private fun decorateModern(method: String, result: JsonObject): JsonObject {
    val meta = (result["_meta"] as? JsonObject).orEmpty() + (McpMetaKeys.SERVER_INFO to encode(server.serverInfo))
    return buildJsonObject {
      result.forEach { (key, value) -> put(key, value) }
      put("resultType", "complete")
      put("_meta", JsonObject(meta))
      if (method in CACHEABLE_METHODS) {
        put("ttlMs", 0)
        put("cacheScope", "private")
      }
    }
  }

  private fun <T> decodeParams(request: JSONRPCRequest, deserializer: KSerializer<T>): T =
    try {
      McpJson.decodeFromJsonElement(deserializer, request.params ?: EmptyJsonObject)
    } catch (e: SerializationException) {
      throw invalidParams("Invalid params for ${request.method}: ${e.message}")
    } catch (e: IllegalArgumentException) {
      throw invalidParams("Invalid params for ${request.method}: ${e.message}")
    }

  private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
    McpJson.encodeToJsonElement(serializer, value).jsonObject

  private fun encode(value: Implementation): JsonElement =
    McpJson.encodeToJsonElement(Implementation.serializer(), value)

  private fun invalidParams(message: String) = McpException(RPCError.ErrorCode.INVALID_PARAMS, message)

  private fun methodNotFound(method: String) =
    McpException(RPCError.ErrorCode.METHOD_NOT_FOUND, "Method not found: $method")

  companion object {
    private val logger = getLogger<McpDispatcher>()

    private val CACHEABLE_METHODS =
      setOf(
        McpMethod.SERVER_DISCOVER,
        McpMethod.TOOLS_LIST,
        McpMethod.PROMPTS_LIST,
        McpMethod.RESOURCES_LIST,
        McpMethod.RESOURCES_TEMPLATES_LIST,
        McpMethod.RESOURCES_READ,
      )
  }
}
