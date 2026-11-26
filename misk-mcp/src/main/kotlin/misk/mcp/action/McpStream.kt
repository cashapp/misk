package misk.mcp.action

import java.net.HttpURLConnection.HTTP_FORBIDDEN
import kotlin.time.Duration
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.WebActionException
import misk.mcp.McpJson
import misk.mcp.MiskMcpServer
import misk.mcp.internal.McpRejection
import misk.mcp.protocol.JSONRPCError
import misk.mcp.protocol.JSONRPCMessage
import misk.mcp.protocol.JSONRPCNotification
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.RPCError
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

/**
 * The Server-Sent Events (SSE) response stream of one MCP POST. [McpStreamManager.withSseChannel] creates it and closes
 * it when the request ends.
 */
@ExperimentalMiskApi
class McpStream
internal constructor(
  val server: MiskMcpServer,
  internal val httpCall: HttpCall,
  private val sendChannel: SendChannel<ServerSentEvent>,
  private val keepAliveInterval: Duration,
) {
  /**
   * Writes [message] to the stream. Use it to answer a request without dispatching it, as an action that rejects some
   * requests itself does. [handleMessage] already sends its response.
   */
  suspend fun send(message: JSONRPCMessage) {
    sendChannel.send(
      ServerSentEvent(event = "message", data = McpJson.encodeToString(JSONRPCMessage.serializer(), message))
    )
  }

  /** Sends [rejection]'s error with its status. Nothing may have been sent before. */
  internal suspend fun reject(rejection: McpRejection) {
    httpCall.statusCode = rejection.status
    send(rejection.error)
  }

  /**
   * Runs [block] while sending SSE comments. Once the client goes away, a comment fails to write, which cancels the web
   * action's coroutine and so [block].
   */
  internal suspend fun <T> withKeepAlive(block: suspend () -> T): T = coroutineScope {
    val keepAlive = launch {
      while (true) {
        delay(keepAliveInterval)
        sendChannel.send(KEEP_ALIVE)
      }
    }
    try {
      block()
    } finally {
      keepAlive.cancel()
    }
  }

  private companion object {
    val KEEP_ALIVE = ServerSentEvent(comments = "keep-alive")
  }
}

/**
 * Handles one client [message] and writes the response to this stream.
 *
 * The handler runs on the calling coroutine and this function returns after it finishes. Notifications and responses
 * from the client get `202 Accepted` with no body.
 *
 * Requests that break the Streamable HTTP rules get a JSON-RPC error with an HTTP error status, before any handler
 * runs:
 * - 403 for an `Origin` that isn't in [misk.mcp.config.McpServerConfig.allowed_origins]
 * - 400 for headers that don't match the body, and for missing or unsupported protocol versions
 * - 404 for a method that the server doesn't serve, in the 2026-07-28 protocol
 */
@ExperimentalMiskApi
suspend fun McpStream.handleMessage(message: JSONRPCMessage) {
  val request = message as? JSONRPCRequest
  if (!originAllowed()) {
    return reject(
      McpRejection(
        HTTP_FORBIDDEN,
        JSONRPCError(id = request?.id, error = RPCError(RPCError.ErrorCode.INVALID_REQUEST, "Origin not allowed")),
      )
    )
  }
  when (message) {
    is JSONRPCRequest -> {
      val rejection = server.dispatcher.validate(message, httpCall.requestHeaders)
      if (rejection != null) {
        reject(rejection)
      } else {
        send(withKeepAlive { server.dispatcher.respond(message) })
      }
    }
    is JSONRPCNotification,
    is JSONRPCResponse,
    is JSONRPCError -> throw AcceptedResponseException()
  }
}

@OptIn(ExperimentalMiskApi::class)
private fun McpStream.originAllowed(): Boolean {
  val allowed = server.config.allowed_origins
  val origin = httpCall.requestHeaders["Origin"] ?: return true
  return allowed.isEmpty() || allowed.any { it.equals(origin, ignoreCase = true) }
}

/** Ends an MCP POST with `202 Accepted` and no body. */
internal class AcceptedResponseException : WebActionException(202, "Accepted")
