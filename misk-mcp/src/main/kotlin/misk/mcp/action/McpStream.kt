package misk.mcp.action

import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.WebActionException
import misk.mcp.McpJson
import misk.mcp.MiskMcpServer
import misk.mcp.protocol.JSONRPCError
import misk.mcp.protocol.JSONRPCMessage
import misk.mcp.protocol.JSONRPCNotification
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.web.sse.ServerSentEvent

/**
 * The Server-Sent Events (SSE) response stream of one MCP POST. [McpStreamManager.withSseChannel] creates it and closes
 * it when the request ends.
 */
@ExperimentalMiskApi
class McpStream internal constructor(val server: MiskMcpServer, private val sendChannel: SendChannel<ServerSentEvent>) {
  /**
   * Writes [message] to the stream. Use it to answer a request without dispatching it, as an action that rejects some
   * requests itself does. [handleMessage] already sends its response.
   */
  suspend fun send(message: JSONRPCMessage) {
    sendChannel.send(
      ServerSentEvent(event = "message", data = McpJson.encodeToString(JSONRPCMessage.serializer(), message))
    )
  }
}

/**
 * Handles one client [message] and writes the response to this stream.
 *
 * The handler runs on the calling coroutine and this function returns after it finishes. Notifications and responses
 * from the client get `202 Accepted` with no body.
 */
@ExperimentalMiskApi
suspend fun McpStream.handleMessage(message: JSONRPCMessage) {
  when (message) {
    is JSONRPCRequest -> send(server.dispatcher.respond(message))
    is JSONRPCNotification,
    is JSONRPCResponse,
    is JSONRPCError -> throw AcceptedResponseException()
  }
}

/** Ends an MCP POST with `202 Accepted` and no body. */
internal class AcceptedResponseException : WebActionException(202, "Accepted")
