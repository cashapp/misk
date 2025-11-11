package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.Method
import jakarta.inject.Inject
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.BadRequestException
import misk.exceptions.NotFoundException
import misk.exceptions.WebActionException
import misk.logging.getLogger
import misk.mcp.McpSessionHandler
import misk.mcp.action.SESSION_ID_HEADER
import misk.mcp.encodeToString
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent
import java.util.UUID
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


/**
 * MCP transport implementation using Server-Sent Events (SSE) over HTTP.
 *
 * Adapts Misk's SSE infrastructure to the MCP Kotlin SDK transport interface.
 * Handles session management for stateless HTTP connections and sends JSON-RPC
 * messages as SSE events to the client.
 *
 * @param call The HTTP call context
 * @param mcpSessionHandler Optional session handler for managing client sessions
 * @param sendChannel Channel for sending SSE events to the client
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalMiskApi::class)
internal class MiskStreamableHttpServerTransport @Inject constructor(
  override val call: HttpCall,
  private val mcpSessionHandler: McpSessionHandler?,
  private val sendChannel: SendChannel<ServerSentEvent>,
) : MiskServerTransport() {

  private val initialized: AtomicBoolean = AtomicBoolean(false)

  override val streamId: String = UUID.randomUUID().toString()

  override suspend fun start() {
    if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
      error(
        """MiskStreamableHttpServerTransport already started!
        | If using Server class, note that connect() calls start() automatically.
        | """.trimMargin()
      )
    }
  }

  override suspend fun send(message: JSONRPCMessage) {
    if (!initialized.load()) {
      error("Not connected")
    }

    val event = ServerSentEvent(
      event = "message",
      data = message.encodeToString()
    )

    logger.trace { "Sending SSE: $event" }
    sendChannel.send(event)
  }

  override suspend fun close() {
    if (initialized.compareAndSet(expectedValue = true, newValue = false)) {
      sendChannel.close()
      _onClose.invoke()
    }
  }

  override suspend fun handleMessage(message: JSONRPCMessage) {
    if (message is JSONRPCRequest) {
      mcpSessionHandler?.handleSession(message)
    }

    try {
      _onMessage.invoke(message)
    } catch (e: Exception) {
      _onError.invoke(e)
      throw e
    }

    message.maybeOverrideResponse()
  }

  private suspend fun McpSessionHandler.handleSession(
    message: JSONRPCRequest
  ) {
    if (message.method == Method.Defined.Initialize.value) {
      // On an initialization request, initialize a new session for the client and return
      // in the SESSION_ID_HEADER response header
      val sessionId = initialize()
      call.setResponseHeader(SESSION_ID_HEADER, sessionId)
    } else {
      // On non-initialization requests, validate that the session ID exists in the request
      // and that it's a valid, active session
      val sessionId = call.requestHeaders[SESSION_ID_HEADER]
        ?: throw BadRequestException("Missing required $SESSION_ID_HEADER header")
      if (!isActive(sessionId)) {
        throw NotFoundException("SessionID $SESSION_ID_HEADER does not exist")
      }
    }
  }

  private fun JSONRPCMessage.maybeOverrideResponse() =
    when (this) {
      is JSONRPCNotification,
      is JSONRPCResponse -> {
        // Notifications and responses should return a 202 if handled successfully with no content
        // Because we default to an SSE response and a server session, we need to end the session and directly
        // return the result. If the handler fails to handle the response or notification, it should throw an
        // error that should be translated to a JSON-RPC error response
        throw AcceptedResponseException()
      }

      else -> Unit
    }

  interface Factory {
    fun create(sendChannel: SendChannel<ServerSentEvent>): MiskStreamableHttpServerTransport
  }

  companion object {
    private val logger = getLogger<MiskStreamableHttpServerTransport>()
  }
}

/** Represents a 202 Accepted response to indicate a notification or response was handled successfully */
internal class AcceptedResponseException : WebActionException(202, "Accepted")

