package misk.mcp.action

import misk.annotation.ExperimentalMiskApi
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes

/**
 * Marks a web action method as an MCP (Model Context Protocol) HTTP POST endpoint for StreamableHTTP transport.
 *
 * This annotation creates endpoints that handle client-to-server JSON-RPC 2.0 messages using StreamableHTTP transport
 * (Server-Sent Events). The endpoint accepts JSON-RPC requests from MCP clients and responds with Server-Sent Events
 * (SSE) for real-time bidirectional communication.
 *
 * ## Transport Compatibility
 *
 * **StreamableHTTP Transport Only**: This annotation is designed exclusively for StreamableHTTP transport using
 * Server-Sent Events. It is the only transport misk-mcp will support once it adopts MCP 2026-07-28.
 *
 * ## Purpose
 * Implements the MCP specification requirement for "sending messages to the server" by providing an HTTP POST endpoint
 * that processes client JSON-RPC 2.0 messages and maintains Server-Sent Events (SSE) connections for server responses.
 *
 * ## Configuration
 *
 * The annotation automatically configures:
 * - **Endpoint**: `POST /mcp`
 * - **Request Content-Type**: `application/json` (for JSON-RPC messages)
 * - **Response Content-Type**: `text/event-stream` (for Server-Sent Events (SSE) responses)
 *
 * ## Handling Messages
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
 * - `sendChannel: SendChannel<ServerSentEvent>` - For Server-Sent Events (SSE) responses
 * - `suspend` modifier for coroutine support
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
 *       // Process client JSON-RPC message and send response via Server-Sent Events (SSE)
 *       handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * @see McpStreamManager For managing MCP streams and server lifecycle
 * @see misk.mcp.MiskMcpServer For the underlying MCP server implementation
 */
@Post("/mcp")
@RequestContentType(MediaTypes.APPLICATION_JSON)
@ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
@Target(AnnotationTarget.FUNCTION)
@ExperimentalMiskApi
annotation class McpPost
