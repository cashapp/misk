package misk.mcp.action

import misk.annotation.ExperimentalMiskApi
import misk.web.ConnectWebSocket

/**
 * Annotation for WebSocket actions that handle Model Context Protocol (MCP) connections.
 *
 * This annotation combines [ConnectWebSocket] with MCP-specific configuration to create WebSocket endpoints that can
 * communicate using the MCP JSON-RPC protocol. It automatically configures the WebSocket to accept connections at the
 * "/mcp" path.
 *
 * ## Usage
 *
 * Apply this annotation to a function in a WebAction class that accepts a [misk.web.actions.WebSocket] parameter. The
 * function should delegate to [misk.mcp.action.McpStreamManager.withWebSocket] to handle the MCP protocol
 * communication:
 * ```kotlin
 * @Singleton
 * class MyMcpWebSocketAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *   @McpWebSocket
 *   fun handle(webSocket: WebSocket) = mcpStreamManager.withWebSocket(webSocket)
 * }
 * ```
 *
 * ## Protocol Support
 *
 * The WebSocket endpoint created by this annotation supports the full MCP specification including:
 * - Tool invocation via JSON-RPC
 * - Resource access and listing
 * - Prompt management and retrieval
 * - Bidirectional communication between client and server
 *
 * ## Configuration
 *
 * The annotation is pre-configured with:
 * - **Path**: `/mcp` - Standard MCP WebSocket endpoint
 * - **Protocol**: JSON-RPC over WebSocket as specified by MCP
 *
 * ## Requirements
 * - The annotated function must be in a class that implements [misk.web.actions.WebAction]
 * - The function must accept exactly one [misk.web.actions.WebSocket] parameter
 * - The WebAction class must be registered with [misk.web.WebActionModule]
 * - MCP server components must be configured via [misk.mcp.McpServerModule]
 *
 * @see misk.web.ConnectWebSocket
 * @see misk.mcp.action.McpStreamManager
 * @see misk.mcp.McpServerModule
 */
@ConnectWebSocket("/mcp") @Target(AnnotationTarget.FUNCTION) @ExperimentalMiskApi annotation class McpWebSocket
