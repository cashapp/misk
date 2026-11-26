package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import jakarta.inject.Inject
import java.util.UUID
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.WebActionException
import misk.logging.getLogger
import misk.mcp.encodeToString
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

/**
 * MCP transport implementation using Server-Sent Events (SSE) over HTTP.
 *
 * Adapts Misk's SSE infrastructure to the MCP Kotlin SDK transport interface and sends JSON-RPC messages as SSE events
 * to the client.
 *
 * @param call The HTTP call context
 * @param sendChannel Channel for sending SSE events to the client
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalMiskApi::class)
internal class MiskStreamableHttpServerTransport
@Inject
constructor(override val call: HttpCall, private val sendChannel: SendChannel<ServerSentEvent>) :
  MiskServerTransport() {

  private val initialized: AtomicBoolean = AtomicBoolean(false)

  override val streamId: String = UUID.randomUUID().toString()

  override suspend fun start() {
    if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
      error(
        """
        |MiskStreamableHttpServerTransport already started!
        | If using Server class, note that connect() calls start() automatically.
        | 
        """
          .trimMargin()
      )
    }
  }

  override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
    if (!initialized.load()) {
      error("Not connected")
    }

    val event = ServerSentEvent(event = "message", data = message.encodeToString())

    logger.trace { "Sending SSE: $event" }
    sendChannel.send(event)
  }

  override suspend fun close() {
    if (initialized.compareAndSet(expectedValue = true, newValue = false)) {
      sendChannel.close()
      invokeOnCloseCallback()
    }
  }

  override suspend fun handleMessage(message: JSONRPCMessage) {
    try {
      _onMessage.invoke(message)
    } catch (e: Exception) {
      _onError.invoke(e)
      throw e
    }

    message.maybeOverrideResponse()
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
