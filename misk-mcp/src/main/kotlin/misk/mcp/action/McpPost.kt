package misk.mcp.action

import misk.annotation.ExperimentalMiskApi
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes

/**
 * Marks a web action method as an MCP (Model Context Protocol) HTTP POST endpoint for StreamableHttp transport.
 *
 * This annotation creates endpoints that handle client-to-server JSON-RPC 2.0 messages using
 * StreamableHttp transport (Server-Sent Events). The endpoint accepts JSON-RPC requests from
 * MCP clients and responds with SSE for real-time bidirectional communication.
 *
 * ## Transport Compatibility
 *
 * **StreamableHttp Transport Only**: This annotation is designed exclusively for StreamableHttp
 * transport using Server-Sent Events. For WebSocket-based MCP communication, use `@McpWebSocket`
 * instead, which handles all communication over a persistent WebSocket connection.
 *
 * ## Purpose
 * Implements the MCP specification requirement for "sending messages to the server" by providing
 * an HTTP POST endpoint that processes client JSON-RPC 2.0 messages and maintains SSE connections
 * for server responses.
 *
 * ## Configuration
 *
 * The annotation automatically configures:
 * - **Endpoint**: `POST /mcp`
 * - **Request Content-Type**: `application/json` (for JSON-RPC messages)
 * - **Response Content-Type**: `text/event-stream` (for SSE responses)
 *
 * ## Session Support
 * The endpoint accepts an optional `Mcp-Session-Id` header (referenced by [SESSION_ID_HEADER])
 * when stateful sessions are used:
 *
 * ```kotlin
 * @McpPost
 * suspend fun handleMcpRequest(
 *   @RequestBody message: JSONRPCMessage,
 *   sendChannel: SendChannel<ServerSentEvent>
 * ) {
 *   mcpStreamManager.withSseChannel(sendChannel) {
 *     // Handle the MCP JSON-RPC message
 *     handleMessage(message)
 *   }
 * }
 * ```
 *
 * ## Method Signature Requirements
 *
 * Methods annotated with `@McpPost` should typically have:
 * - `@RequestBody message: JSONRPCMessage` - For JSON-RPC 2.0 message processing
 * - `sendChannel: SendChannel<ServerSentEvent>` - For SSE responses
 * - `suspend` modifier for coroutine support
 * - Optional `@RequestHeaders headers: Headers` for session ID extraction
 *
 * ## MCP Protocol Support
 * Handles all standard MCP JSON-RPC 2.0 operations:
 * - **initialize**: Server capability negotiation
 * - **tools/list**: Available tool discovery
 * - **tools/call**: Tool execution requests
 * - **resources/list**: Available resource discovery
 * - **resources/read**: Resource content retrieval
 * - **prompts/list**: Available prompt discovery
 * - **prompts/get**: Prompt template retrieval
 *
 * ## Example Usage
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
 *     mcpStreamManager.withSseChannel(sendChannel) {
 *       // Process client JSON-RPC message and send response via SSE
 *       handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * @see McpGet for server-to-client event streaming
 * @see McpDelete for session termination
 * @see SESSION_ID_HEADER for the session ID header constant
 * @see McpStreamManager For managing MCP streams and server lifecycle
 * @see misk.mcp.MiskMcpServer For the underlying MCP server implementation
 */
@Post("/mcp")
@RequestContentType(MediaTypes.APPLICATION_JSON)
@ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
@Target(AnnotationTarget.FUNCTION)
@ExperimentalMiskApi
annotation class McpPost
