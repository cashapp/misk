package misk.mcp.action

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.logging.getLogger
import misk.mcp.MiskMcpServer
import misk.mcp.internal.MiskServerTransport
import misk.mcp.internal.MiskSseServerSession
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.sse.ServerSentEvent

@Suppress("unused")
const val SESSION_ID_PARAM = "Mcp-Session-Id"

/**
 * Manages MCP (Model Context Protocol) server sessions and SSE (Server-Sent Events) connections.
 *
 * This class provides the bridge between Misk web actions and MCP server instances, handling
 * the lifecycle of SSE connections and ensuring proper setup and teardown of MCP server
 * transport layers. Each client connection gets its own session with a unique session ID.
 *
 * ## SSE Session Lifecycle
 *
 * 1. **Connection Establishment**: Client initiates SSE connection to `/mcp` endpoint
 * 2. **Session Creation**: [MiskSseServerSession] is created with unique session ID
 * 3. **Transport Setup**: [MiskServerTransport] is configured to handle JSON-RPC messages
 * 4. **Server Connection**: [MiskMcpServer] is connected to the transport
 * 5. **Message Handling**: Client messages are processed through the server
 * 6. **Connection Cleanup**: Transport and session are properly closed when done
 *
 * ## Usage in Web Actions
 *
 * Use this manager in web actions that handle MCP client requests. The [withResponseChannel]
 * method provides a [MiskMcpServer] instance configured for the current session:
 *
 * ```kotlin
 * @Singleton
 * class McpWebAction @Inject constructor(
 *   private val mcpSessionManager: McpSessionManager
 * ) : WebAction {
 *
 *   @McpPost
 *   suspend fun handleMcpRequest(
 *     @RequestBody message: JSONRPCMessage,
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpSessionManager.withResponseChannel(sendChannel) {
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
 * ### Stateful Session Handling
 * ```kotlin
 * @McpPost
 * suspend fun handleStatefulMcp(
 *   @RequestBody message: JSONRPCMessage,
 *   sendChannel: SendChannel<ServerSentEvent>
 * ) {
 *   mcpSessionManager.withResponseChannel(sendChannel) {
 *     // Access session information
 *     val sessionId = (transport as MiskServerTransport).sessionId
 *     logger.info { "Processing message for session: $sessionId" }
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
 *   mcpSessionManager.withResponseChannel(sendChannel) {
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
 * ## Session Context
 *
 * Within the [withResponseChannel] block, the context provides:
 * - **this**: [MiskMcpServer] instance for handling MCP protocol messages
 * - **transport**: [MiskServerTransport] for low-level transport operations
 * - **sessionId**: Unique identifier for the current client session
 * - **sendChannel**: Direct access to SSE response channel (if needed)
 *
 * ## Thread Safety
 *
 * This manager is thread-safe and can handle multiple concurrent client sessions.
 * Each session gets its own isolated [MiskMcpServer] instance and transport.
 *
 * @see MiskMcpServer For the MCP server implementation
 * @see McpPost For the web action annotation
 * @see MiskServerTransport For the underlying transport mechanism
 * @see MiskSseServerSession For SSE session management
 */
@ExperimentalMiskApi
@Singleton
class McpSessionManager @Inject constructor(
  private val httpCall: ActionScoped<HttpCall>,
  private val mcpServerProvider: Provider<MiskMcpServer>
) {

  suspend fun withResponseChannel(
    sendChannel: SendChannel<ServerSentEvent>,
    block: suspend MiskMcpServer.() -> Unit
  ) {
    val session = MiskSseServerSession(
      call = httpCall.get(),
      sendChannel = sendChannel,
    )
    val transport = MiskServerTransport(session)
    logger.debug { "New SSE connection established with sessionId: ${transport.sessionId}" }

    val server = mcpServerProvider.get().apply {
      onClose {
        logger.debug { "Server connection closed for sessionId: ${transport.sessionId}" }
      }
      logger.debug { "Server connected to transport for sessionId: ${transport.sessionId}" }
      connect(transport)
    }

    try {
      block(server)
    } finally {
      transport.close()
    }
  }

  companion object {
    private val logger = getLogger<McpSessionManager>()
  }
}
