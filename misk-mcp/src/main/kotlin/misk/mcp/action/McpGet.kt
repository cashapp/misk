package misk.mcp.action

import misk.annotation.ExperimentalMiskApi
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes

/**
 * Marks a web action method as an MCP (Model Context Protocol) HTTP GET endpoint for StreamableHttp transport.
 *
 * This annotation creates endpoints that allow clients to listen for messages sent from the MCP server
 * using StreamableHttp transport (Server-Sent Events). The endpoint establishes an SSE connection
 * for real-time server-to-client communication.
 *
 * ## Transport Compatibility
 *
 * **StreamableHttp Transport Only**: This annotation is designed exclusively for StreamableHttp
 * transport using Server-Sent Events. For WebSocket-based MCP communication, use `@McpWebSocket`
 * instead, which handles all communication over a persistent WebSocket connection.
 *
 * ## Purpose
 * Implements the MCP specification requirement for "listening for messages from the server"
 * by providing an SSE endpoint where clients can receive server-initiated events and notifications.
 *
 * ## Configuration
 *
 * The annotation automatically configures:
 * - **Endpoint**: `GET /mcp`
 * - **Response Content-Type**: `text/event-stream` (for SSE responses)
 * - **No Request Body**: GET requests don't accept request bodies
 *
 * ## Session Support
 * The endpoint accepts an optional `Mcp-Session-Id` header (referenced by [SESSION_ID_HEADER])
 * when stateful sessions are used:
 *
 * ```kotlin
 * @McpGet
 * suspend fun listenForServerMessages(
 *   sendChannel: SendChannel<ServerSentEvent>
 * ) {
 *   mcpStreamManager.withSseChannel(sendChannel) {
 *     // Stream server events for this session
 *   }
 * }
 * ```
 *
 * ## Method Signature Requirements
 *
 * Methods annotated with `@McpGet` should typically have:
 * - `sendChannel: SendChannel<ServerSentEvent>` - For SSE responses
 * - `suspend` modifier for coroutine support
 * - Optional `@RequestHeaders headers: Headers` for session ID extraction
 *
 * ## Use Cases
 * - Server-initiated notifications
 * - Progress updates for long-running operations
 * - Resource change notifications
 * - Tool execution status updates
 *
 * ## Example Usage
 *
 * ```kotlin
 * @Singleton
 * class McpWebAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpGet
 *   suspend fun streamServerEvents(
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpStreamManager.withSseChannel(sendChannel) {
 *       // Client connects to listen for server-initiated messages
 *       // Server can push notifications, progress updates, etc.
 *     }
 *   }
 * }
 * ```
 *
 * @see McpPost for handling client-to-server JSON-RPC 2.0 messages
 * @see McpDelete for session termination
 * @see SESSION_ID_HEADER for the session ID header constant
 * @see McpStreamManager For managing MCP streams and server lifecycle
 * @see misk.mcp.MiskMcpServer For the underlying MCP server implementation
 */
@Get("/mcp")
@ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
@Target(AnnotationTarget.FUNCTION)
@ExperimentalMiskApi
annotation class McpGet
