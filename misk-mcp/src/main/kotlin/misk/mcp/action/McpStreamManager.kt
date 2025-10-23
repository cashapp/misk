package misk.mcp.action

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import misk.annotation.ExperimentalMiskApi
import misk.logging.getLogger
import misk.mcp.MiskMcpServer
import misk.mcp.internal.McpJson
import misk.mcp.internal.McpServerSession
import misk.mcp.internal.MiskServerTransport
import misk.mcp.internal.MiskStreamableHttpServerTransport
import misk.mcp.internal.MiskWebSocketServerTransport
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.sse.ServerSentEvent

@Suppress("unused")


/**
 * Manages MCP (Model Context Protocol) server connections for both SSE and WebSocket transports.
 *
 * This class provides the bridge between Misk web actions and MCP server instances, handling
 * the lifecycle of client connections and ensuring proper setup and teardown of MCP server
 * transport layers. Each client connection gets its own isolated server instance with a unique stream ID.
 *
 * ## Supported Transport Types
 *
 * ### Server-Sent Events (SSE) via HTTP
 * - Used with [McpPost] annotated actions
 * - Supports streamable HTTP transport for real-time communication
 * - Client sends JSON-RPC messages via POST, server responds via SSE stream
 *
 * ### WebSocket
 * - Used with [McpWebSocket] annotated actions  
 * - Full bidirectional communication over persistent WebSocket connection
 * - Both client and server can initiate communication
 *
 * ## Connection Lifecycle
 *
 * 1. **Connection Establishment**: Client initiates connection (SSE or WebSocket) to `/mcp` endpoint
 * 2. **Transport Creation**: Appropriate transport ([MiskStreamableHttpServerTransport] or [MiskWebSocketServerTransport]) is created
 * 3. **Server Initialization**: [MiskMcpServer] instance is created and connected to transport
 * 4. **Message Handling**: Client messages are processed through the MCP server
 * 5. **Connection Cleanup**: Transport and server resources are properly closed when connection ends
 *
 * ## SSE Usage in Web Actions
 *
 * Use [withSseChannel] in web actions that handle MCP client requests via Server-Sent Events:
 *
 * ```kotlin
 * @Singleton
 * class McpSseAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpPost
 *   suspend fun handleMcpRequest(
 *     @RequestBody message: JSONRPCMessage,
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpStreamManager.withSseChannel(sendChannel) {
 *       // 'this' context is MiskMcpServer
 *       // Handle the incoming JSON-RPC message
 *       handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * ## WebSocket Usage in Web Actions
 *
 * Use [withWebSocket] in web actions that handle MCP client connections via WebSocket:
 *
 * ```kotlin
 * @Singleton
 * class McpWebSocketAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpWebSocket
 *   fun handleWebSocket(webSocket: WebSocket): WebSocketListener {
 *     return mcpStreamManager.withWebSocket(webSocket)
 *   }
 * }
 * ```
 *
 * ## Advanced SSE Usage Examples
 *
 * ### Stateful Stream Handling
 * ```kotlin
 * @McpPost
 * suspend fun handleStatefulMcp(
 *   @RequestBody message: JSONRPCMessage,
 *   sendChannel: SendChannel<ServerSentEvent>
 * ) {
 *   mcpStreamManager.withSseChannel(sendChannel) {
 *     // Access stream information
 *     val streamId = transport.streamId
 *     logger.info { "Processing message for stream: $streamId" }
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
 *   mcpStreamManager.withSseChannel(sendChannel) {
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
 * Within the [withSseChannel] block, the context provides:
 * - **this**: [MiskMcpServer] instance for handling MCP protocol messages
 * - **transport**: Access to the underlying transport with `streamId`
 * - **sendChannel**: Direct access to SSE response channel (passed as parameter)
 *
 * For WebSocket connections, the [withWebSocket] method returns a [WebSocketListener] that
 * automatically handles message routing to the MCP server instance.
 *
 * ## Thread Safety
 *
 * This manager is thread-safe and can handle multiple concurrent client connections.
 * Each connection gets its own isolated [MiskMcpServer] instance and transport.
 *
 * ## Migration Note
 *
 * The [withResponseChannel] method is deprecated in favor of [withSseChannel] for clarity.
 *
 * @see MiskMcpServer For the MCP server implementation
 * @see McpPost For SSE-based web action annotation
 * @see McpWebSocket For WebSocket-based web action annotation
 * @see MiskStreamableHttpServerTransport For SSE transport implementation
 * @see MiskWebSocketServerTransport For WebSocket transport implementation
 */
@ExperimentalMiskApi
class McpStreamManager internal constructor(
  private val streamableHttpServerTransportFactory: MiskStreamableHttpServerTransport.Factory,
  private val webSocketsServerTransportFactory: MiskWebSocketServerTransport.Factory,
  private val mcpServer: MiskMcpServer,
) {
  @Deprecated(
    message = "Use withSseChannel instead",
    replaceWith = ReplaceWith("withSseChannel")
  )
  suspend fun withResponseChannel(
    sendChannel: SendChannel<ServerSentEvent>,
    block: suspend ServerSession.() -> Unit
  ) = withSseChannel(sendChannel,block)

  /**
   * Handles MCP communication over Server-Sent Events transport.
   *
   * Use in [McpPost] annotated web actions to process MCP JSON-RPC messages:
   *
   * ```kotlin
   * @McpPost
   * suspend fun handleMcp(
   *   @RequestBody message: JSONRPCMessage,
   *   sendChannel: SendChannel<ServerSentEvent>
   * ) {
   *   mcpStreamManager.withSseChannel(sendChannel) {
   *     handleMessage(message) // 'this' is MiskMcpServer
   *   }
   * }
   * ```
   *
   * @param sendChannel SSE channel for sending responses to the client
   * @param block Function executed with [MiskMcpServer] as receiver context
   */
  suspend fun withSseChannel(
    sendChannel: SendChannel<ServerSentEvent>,
    block: suspend ServerSession.() -> Unit
  ) {

    val transport = streamableHttpServerTransportFactory.create(sendChannel).also {
      logger.debug { "New SSE connection established with streamId: ${it.streamId}" }
    }


    val session = mcpServer.connect(transport).initialize()

    try {
      withContext(McpServerSession(session)) {
        block(session)
      }
    } finally {
      transport.close()
    }
  }

  /**
   * Handles MCP communication over WebSocket transport.
   *
   * Use in [McpWebSocket] annotated web actions to manage bidirectional MCP connections:
   *
   * ```kotlin
   * @McpWebSocket
   * fun handleWebSocket(webSocket: WebSocket): WebSocketListener {
   *   return mcpStreamManager.withWebSocket(webSocket)
   * }
   * ```
   *
   * @param webSocket WebSocket connection to manage
   * @return WebSocketListener that automatically handles MCP protocol messages
   */
  fun withWebSocket(webSocket: WebSocket): WebSocketListener = object : WebSocketListener() {


    private val webSocketTransport = webSocketsServerTransportFactory.create(webSocket).also {
      logger.debug { "New Websocket connection established with streamId: ${it.streamId}" }
    }
    private val serverSession = runBlocking { mcpServer.connect(webSocketTransport) }.initialize()

    // Because there may be requests/responses sent inline over the websocket,
    // we need to move the handling to a different thread so that websocket handling
    // thread is freed for additional messages.
    private val coroutineScope = CoroutineScope(
      Dispatchers.IO.limitedParallelism(1)
          + McpServerSession(serverSession)
    )
    
    override fun onMessage(webSocket: WebSocket, text: String) {
      val message: JSONRPCMessage = McpJson.decodeFromString(text)
      coroutineScope.launch { serverSession.handleMessage(message) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
      coroutineScope.cancel("closing")
      runBlocking(McpServerSession(serverSession)) { webSocketTransport.close() }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable) {
      coroutineScope.cancel("failure", t)
      runBlocking(McpServerSession(serverSession)) { webSocketTransport.close() }
    }
  }

  /**
   * Handles internal initialization of a new [ServerSession].
   */
  private fun ServerSession.initialize(): ServerSession {
    val miskServerTransport = checkNotNull(transport as? MiskServerTransport) {
      "Expected transport to be MiskServerTransport but was ${transport?.let { it::class.simpleName }}"
    }
    val serverName = mcpServer.name
    val streamId = miskServerTransport.streamId

    onInitialized {
      logger.info { "Initialized mcp server session for '$serverName' with stream id '$streamId'" }
    }
    onClose {
      logger.info { "Closed mcp server session for '$serverName' with stream id '$streamId'" }
    }
    logger.debug { "Server Session connected to transport with stream id '$streamId'" }
    return this
  }

  companion object {
    private val logger = getLogger<McpStreamManager>()
  }
}

