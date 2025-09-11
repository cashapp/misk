package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


/**
 * Misk-specific transport implementation that bridges MCP Kotlin SDK with SSE sessions.
 *
 * Acts as the glue between the MCP server protocol and the underlying SSE session,
 * handling message serialization and session lifecycle management.
 *
 * @param session The SSE session to send messages through
 */
@OptIn(ExperimentalAtomicApi::class)
internal class MiskServerTransport(
  internal val stream: MiskSseServerStream,
) : AbstractTransport() {

  private val initialized: AtomicBoolean = AtomicBoolean(false)

  val streamId: String
    get() = stream.streamId

  override suspend fun start() {
    if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
      error("SSEServerTransport already started! If using Server class, note that connect() calls start() automatically.")
    }
  }

  override suspend fun send(message: JSONRPCMessage) {
    if (!initialized.load()) {
      error("Not connected")
    }

    stream.send(
      event = "message",
      data = McpJson.encodeToString(message),
    )
  }

  override suspend fun close() {
    if (initialized.compareAndSet(expectedValue = true, newValue = false)) {
      stream.close()
      _onClose.invoke()
    }
  }

  suspend fun handleMessage(message: JSONRPCMessage, sessionId: String? = null) {
    try {
      _onMessage.invoke(message)
    } catch (e: Exception) {
      _onError.invoke(e)
      throw e
    }
  }
}


