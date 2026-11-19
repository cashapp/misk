package misk.mcp.internal

import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import misk.MiskCaller
import misk.annotation.ExperimentalMiskApi
import misk.mcp.action.McpStream
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.McpException
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.RequestMeta

/** The MCP request a handler is serving, and the stream its response goes to. */
@OptIn(ExperimentalMiskApi::class)
internal class McpRequestContext(val stream: McpStream, val request: JSONRPCRequest, private val caller: MiskCaller?) :
  AbstractCoroutineContextElement(Key) {
  val era: McpEra = request.era()

  val meta: RequestMeta?
    get() = request.meta()

  /**
   * What [misk.mcp.inputRequired] threw, so the dispatcher can still end the request with it if the handler catches it.
   */
  var inputSignal: RuntimeException? = null

  val inputResponses: JsonObject?
    get() = request.params?.get(INPUT_RESPONSES) as? JsonObject

  /** Identifies this request and its caller across the rounds of a multi round-trip request. */
  val binding: String by lazy {
    val params = JsonObject(request.params.orEmpty() - setOf("_meta", INPUT_RESPONSES, REQUEST_STATE))
    val identity = buildJsonObject {
      put("principal", caller?.principal)
      put("method", request.method)
      put("params", params.canonical())
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.toString().toByteArray(Charsets.UTF_8))
    Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }

  /** The state the client echoed from the previous round, once verified. */
  val requestState: JsonElement? by lazy {
    val token = (request.params?.get(REQUEST_STATE) as? JsonPrimitive)?.takeIf { it.isString }?.content
    token?.let {
      val codec =
        stream.server.requestStateCodec
          ?: throw McpException(RPCError.ErrorCode.INVALID_PARAMS, "This server doesn't accept requestState")
      codec.open(it, binding)
    }
  }

  companion object Key : CoroutineContext.Key<McpRequestContext> {
    private const val INPUT_RESPONSES = "inputResponses"
    private const val REQUEST_STATE = "requestState"
  }
}

/** Sorts object keys, so that equal JSON values have equal text. */
private fun JsonElement.canonical(): JsonElement =
  when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { (key, value) -> key to value.canonical() })
    is JsonArray -> JsonArray(map { it.canonical() })
    else -> this
  }
