package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import misk.logging.getLogger
import misk.web.HttpCall
import misk.web.actions.WebSocket
import java.util.UUID
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


/**
 * MCP transport implementation using WebSockets.
 *
 * Adapts Misk's WebSocket infrastructure to the MCP Kotlin SDK transport interface.
 * Provides persistent bidirectional communication with MCP subprotocol validation.
 * Unlike SSE transport, WebSockets maintain persistent connections without the need for
 * cross call session management.
 *
 * @param call The HTTP call context for the WebSocket upgrade
 * @param webSocket The WebSocket connection for bidirectional communication
 */
@OptIn(ExperimentalAtomicApi::class)
internal class MiskWebSocketServerTransport(
  override val call: HttpCall,
  private val webSocket: WebSocket,
) : MiskServerTransport() {

  private val initialized: AtomicBoolean = AtomicBoolean(false)

  override val streamId: String = UUID.randomUUID().toString()

  override suspend fun start() {

    if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
      error(
        """MiskWebSocketServerTransport already started!
        | If using Server class, note that connect() calls start() automatically.
        | """.trimMargin())
    }

    call.verifySubprotocol()
  }

  override suspend fun send(message: JSONRPCMessage) {
    if (!initialized.load()) {
      error("Not connected")
    }

    val data  = McpJson.encodeToString(message)
    logger.trace { "Sending WebSocket message: $data" }
    webSocket.send(data)
  }

  override suspend fun close() {
    if (initialized.compareAndSet(expectedValue = true, newValue = false)) {
      webSocket.close(1000, null)
      _onClose.invoke()
    }
  }

  override suspend fun handleMessage(message: JSONRPCMessage) {
    try {
      _onMessage.invoke(message)
    } catch (e: Exception) {
      _onError.invoke(e)
      throw e
    }
  }

  private fun HttpCall.verifySubprotocol(){
    val subprotocol = requestHeaders[SEC_WEBSOCKET_PROTOCOL_HEADER]
    require(subprotocol == MCP_SUBPROTOCOL){
      "Invalid subprotocol: $subprotocol, expected $MCP_SUBPROTOCOL"
    }
  }

  interface Factory {
    fun create(webSocket: WebSocket): MiskWebSocketServerTransport
  }

  companion object {
    private val logger = getLogger<MiskWebSocketServerTransport>()
    const val SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol"
    const val MCP_SUBPROTOCOL: String = "mcp"
  }
}



