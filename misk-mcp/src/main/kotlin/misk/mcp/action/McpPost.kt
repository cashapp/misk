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
 * (SSE).
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
 * ## Method Signature Requirements
 *
 * Methods annotated with `@McpPost` should typically have:
 * - `@RequestBody message: JSONRPCMessage` - For JSON-RPC 2.0 message processing
 * - `sendChannel: SendChannel<ServerSentEvent>` - For Server-Sent Events (SSE) responses
 * - `suspend` modifier for coroutine support
 *
 * ## MCP Protocol Support
 * Serves MCP 2026-07-28 (`server/discover`, with the protocol version in each request's `_meta`) and the 2025 versions
 * (`initialize`, `ping`), and in both: `tools/list`, `tools/call`, `resources/list`, `resources/templates/list`,
 * `resources/read`, `prompts/list` and `prompts/get`. See [handleMessage] for the HTTP statuses of invalid requests.
 *
 * `GET` and `DELETE` are not served: the server opens no standalone SSE streams and keeps no sessions.
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
