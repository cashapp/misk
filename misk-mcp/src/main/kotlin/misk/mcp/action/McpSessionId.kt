package misk.mcp.action

import jakarta.inject.Inject
import misk.api.HttpRequest
import misk.scope.ActionScoped

const val SESSION_ID_HEADER = "Mcp-Session-Id"

/**
 * Provides access to the current MCP session ID within action-scoped contexts.
 *
 * This class can be injected into MCP server features such as tools and resources to retrieve the session identifier
 * for the current request. The session ID is extracted from the "Mcp-Session-Id" header that is automatically managed
 * by the framework when an [misk.mcp.McpSessionHandler] is installed.
 *
 * ## Prerequisites
 *
 * This class should only be injected when an [misk.mcp.McpSessionHandler] has been installed via
 * [misk.mcp.McpSessionHandlerModule]. Without a session handler, the session ID header will not be present and [get]
 * will throw an exception.
 *
 * ## Usage in MCP Tools
 *
 * ```kotlin
 * @Singleton
 * class SessionAwareTool @Inject constructor(
 *   private val sessionId: Provider<McpSessionId>,
 *   private val dataService: DataService
 * ) : McpTool {
 *   override val name = "get_session_data"
 *   override val description = "Retrieves data for the current session"
 *
 *   override suspend fun call(arguments: JsonNode): JsonNode {
 *     val currentSessionId = sessionId.get().get()
 *     val data = dataService.getDataForSession(currentSessionId)
 *     return objectMapper.valueToTree(data)
 *   }
 * }
 * ```
 *
 * ## Error Handling
 *
 * If no session handler is installed or the session ID header is missing, [get] will throw an [IllegalStateException]
 * with a descriptive error message.
 *
 * @see misk.mcp.McpSessionHandler for session management
 * @see misk.mcp.McpSessionHandlerModule for installation
 */
class McpSessionId @Inject constructor(private val httpRequest: ActionScoped<HttpRequest>) {
  /**
   * Retrieves the current MCP session ID from the request headers.
   *
   * The session ID is extracted from the "Mcp-Session-Id" header that is automatically set by the framework when a
   * session handler is active.
   *
   * @return The current session identifier
   * @throws IllegalStateException if no session ID is found in the headers, typically indicating that no
   *   [misk.mcp.McpSessionHandler] has been registered
   */
  fun get(): String =
    httpRequest.get().requestHeaders[SESSION_ID_HEADER]
      ?: throw IllegalStateException(
        "No MCP session ID found in the '$SESSION_ID_HEADER' header, did you register an McpSessionHandler?"
      )
}
