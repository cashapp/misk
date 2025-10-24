package misk.mcp.action

import misk.annotation.ExperimentalMiskApi
import misk.web.Delete
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes

/**
 * Annotation for web action methods that handle MCP session deletion for StreamableHttp transport.
 *
 * This annotation configures a web action to handle explicit MCP session termination for
 * StreamableHttp transport (Server-Sent Events) as specified in the MCP specification for
 * session management. The client sends a DELETE request with the session ID to cleanly
 * terminate an existing MCP session.
 *
 * ## Transport Compatibility
 *
 * **StreamableHttp Transport Only**: This annotation is designed exclusively for StreamableHttp
 * transport using Server-Sent Events. WebSocket transport (`@McpWebSocket`) does not use
 * explicit session deletion endpoints, as session termination is handled automatically when
 * the WebSocket connection is closed.
 *
 * ## Configuration
 *
 * The annotation automatically configures:
 * - **Endpoint**: `DELETE /mcp`
 * - **Response Content-Type**: `text/plain; charset=utf-8`
 * - **Session ID**: Retrieved from `Mcp-Session-Id` header
 *
 * ## Usage
 *
 * Use this annotation for MCP session termination endpoints:
 *
 * ```kotlin
 * @Singleton
 * class McpWebAction @Inject constructor() : WebAction {
 *
 *   @McpDelete
 *   suspend fun deleteSession(
 *     @HeaderParam(SESSION_ID_PARAM) sessionId: String
 *   ): String {
 *     // Clean up the specified session
 *     sessionManager.terminateSession(sessionId)
 *     return "Session $sessionId terminated"
 *   }
 * }
 * ```
 *
 * ## Method Signature Requirements
 *
 * Methods annotated with `@McpDelete` should typically have:
 * - `@HeaderParam(SESSION_ID_PARAM) sessionId: String` - The session ID from the header
 * - Return type of `String` for status confirmation
 * - `suspend` modifier for coroutine support (if needed)
 *
 * ## MCP Specification Compliance
 *
 * This implements point 5 of the MCP specification's session management:
 * "Clients can send a DELETE request to explicitly terminate a session"
 *
 * The session ID is passed via the `Mcp-Session-Id` header as defined in the
 * MCP transport specification.
 *
 * ## Session Cleanup
 *
 * When implementing session deletion:
 * - Close any open SSE connections for the session
 * - Release server resources associated with the session
 * - Clean up session-specific data and state
 * - Return confirmation of successful termination
 *
 * @see SESSION_ID_HEADER For the header constant ("Mcp-Session-Id")
 * @see McpStreamManager For managing MCP streams and server lifecycle
 * @see McpPost For full MCP protocol request handling
 * @see McpGet For read-only MCP endpoints
 */
@Delete("/mcp")
@ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
@Target(AnnotationTarget.FUNCTION)
@ExperimentalMiskApi
annotation class McpDelete
