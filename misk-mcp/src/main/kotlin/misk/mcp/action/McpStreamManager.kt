package misk.mcp.action

import com.google.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.logging.getLogger
import misk.mcp.MiskMcpServer
import misk.mcp.internal.MiskServerTransport
import misk.mcp.internal.MiskSseServerStream
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

@Suppress("unused")


/**
 * Manages MCP (Model Context Protocol) server streams and SSE (Server-Sent Events) connections.
 *
 * This class provides the bridge between Misk web actions and MCP server instances, handling
 * the lifecycle of SSE connections and ensuring proper setup and teardown of MCP server
 * transport layers. Each client connection gets its own stream with a unique session ID.
 *
 * ## SSE Stream Lifecycle
 *
 * 1. **Connection Establishment**: Client initiates SSE connection to `/mcp` endpoint
 * 2. **Stream Creation**: [MiskSseServerStream] is created with unique session ID
 * 3. **Transport Setup**: [MiskServerTransport] is configured to handle JSON-RPC messages
 * 4. **Server Connection**: [MiskMcpServer] is connected to the transport
 * 5. **Message Handling**: Client messages are processed through the server
 * 6. **Connection Cleanup**: Transport and stream are properly closed when done
 *
 * ## Usage in Web Actions
 *
 * Use this manager in web actions that handle MCP client requests. The [withResponseChannel]
 * method provides a [MiskMcpServer] instance configured for the current stream:
 *
 * ```kotlin
 * @Singleton
 * class McpWebAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpPost
 *   suspend fun handleMcpRequest(
 *     @RequestBody message: JSONRPCMessage,
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpStreamManager.withResponseChannel(sendChannel) {
 *       // 'this' context is MiskMcpServer
 *       // Handle the incoming JSON-RPC message
 *       handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * ## Advanced Usage Examples
 *
 * ### Stateful Stream Handling
 * ```kotlin
 * @McpPost
 * suspend fun handleStatefulMcp(
 *   @RequestBody message: JSONRPCMessage,
 *   sendChannel: SendChannel<ServerSentEvent>
 * ) {
 *   mcpStreamManager.withResponseChannel(sendChannel) {
 *     // Access stream information
 *     val sessionId = (transport as MiskServerTransport).sessionId
 *     logger.info { "Processing message for stream: $sessionId" }
 *
 *     // Handle different message types
 *     when (message.method) {
 *       "tools/call" -> handleMessage(message)
 *       "resources/read" -> handleMessage(message)
 *       else -> handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * ### Custom Error Handling
 * ```kotlin
 * @McpPost
 * suspend fun handleMcpWithErrorHandling(
 *   @RequestBody message: JSONRPCMessage,
 *   sendChannel: SendChannel<ServerSentEvent>
 * ) {
 *   mcpStreamManager.withResponseChannel(sendChannel) {
 *     try {
 *       handleMessage(message)
 *     } catch (e: Exception) {
 *       logger.error(e) { "Error processing MCP message" }
 *       // Server will automatically send error response
 *       throw e
 *     }
 *   }
 * }
 * ```
 *
 * ## Stream Context
 *
 * Within the [withResponseChannel] block, the context provides:
 * - **this**: [MiskMcpServer] instance for handling MCP protocol messages
 * - **transport**: [MiskServerTransport] for low-level transport operations
 * - **sessionId**: Unique identifier for the current client stream
 * - **sendChannel**: Direct access to SSE response channel (if needed)
 *
 * ## Thread Safety
 *
 * This manager is thread-safe and can handle multiple concurrent client streams.
 * Each stream gets its own isolated [MiskMcpServer] instance and transport.
 *
 * @see MiskMcpServer For the MCP server implementation
 * @see McpPost For the web action annotation
 * @see MiskServerTransport For the underlying transport mechanism
 * @see MiskSseServerStream For SSE stream management
 */
@ExperimentalMiskApi
@Singleton
class McpStreamManager(
  private val httpCall: ActionScoped<HttpCall>,
  private val mcpServerProvider: Provider<MiskMcpServer>,
) {
  suspend fun withResponseChannel(
    sendChannel: SendChannel<ServerSentEvent>,
    block: suspend MiskMcpServer.() -> Unit
  ) {
    val session = MiskSseServerStream(
      call = httpCall.get(),
      sendChannel = sendChannel,
    )
    val transport = MiskServerTransport(session)
    logger.debug { "New SSE connection established with sessionId: ${transport.streamId}" }

    val server = mcpServerProvider.get().apply {
      onClose {
        logger.debug { "Server connection closed for sessionId: ${transport.streamId}" }
      }
      logger.debug { "Server connected to transport for sessionId: ${transport.streamId}" }
      connect(transport)
    }

    try {
      block(server)
    } finally {
      transport.close()
    }
  }

  companion object {
    private val logger = getLogger<McpStreamManager>()
  }
}
