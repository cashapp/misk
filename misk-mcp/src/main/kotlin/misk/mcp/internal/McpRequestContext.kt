package misk.mcp.internal

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.JsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.action.McpStream
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.RequestMeta

/** The MCP request a handler is serving, and the stream its response goes to. */
@OptIn(ExperimentalMiskApi::class)
internal class McpRequestContext(val stream: McpStream, val request: JSONRPCRequest) :
  AbstractCoroutineContextElement(Key) {
  val meta: RequestMeta?
    get() = (request.params?.get("_meta") as? JsonObject)?.let(::RequestMeta)

  companion object Key : CoroutineContext.Key<McpRequestContext>
}
