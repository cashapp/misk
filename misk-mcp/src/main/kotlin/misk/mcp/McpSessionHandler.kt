package misk.mcp

/**
 * Handles MCP (Model Context Protocol) session lifecycle management.
 *
 * This interface provides session management capabilities for MCP servers, allowing for
 * session creation, validation, and termination. When installed via [McpSessionHandlerModule],
 * the framework automatically integrates with the session lifecycle and returns the session ID
 * in the "Mcp-Session-Id" response header.
 *
 * ## Framework Integration
 *
 * The framework automatically handles:
 * - Calling [initialize] when a new session is needed
 * - Calling [isActive] to validate existing sessions
 * - Including the session ID in the "Mcp-Session-Id" response header
 *
 * ## Manual Session Termination
 *
 * The [terminate] method can be called:
 * - Directly by server code when needed
 * - In `@McpDelete` web actions for client-directed session termination
 *
 * ## Implementation Notes
 *
 * - All methods are suspend functions to support async operations
 * - Session IDs should be unique and cryptographically secure
 * - Implementations should handle concurrent access safely
 * - Consider session timeout and cleanup strategies
 *
 * ## MCP Specification
 *
 * This implementation follows the MCP specification for session management as defined in
 * section 2.5 of the transport specification:
 * <https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management>
 *
 * @see McpSessionHandlerModule for installation instructions
 */
interface McpSessionHandler {
  /**
   * Initializes a new MCP session and generates a unique session identifier.
   *
   * This method is called automatically by the framework when a new session is required.
   * The returned session ID should be unique across all active sessions and should be
   * cryptographically secure to prevent session hijacking.
   *
   * @return A unique session identifier that will be used for subsequent session operations
   */
  suspend fun initialize(): String

  /**
   * Validates whether the specified session is still active and valid.
   *
   * This method is called automatically by the framework to verify session validity
   * before processing requests. Implementations should check if the session exists,
   * hasn't expired, and is in a valid state.
   *
   * @param sessionId The session identifier to validate
   * @return `true` if the session is active and valid, `false` otherwise
   */
  suspend fun isActive(sessionId: String): Boolean

  /**
   * Terminates the specified session, cleaning up any associated resources.
   *
   * This method can be called:
   * - Directly by server code when session termination is needed
   * - In `@McpDelete` web actions when clients request session termination
   * - By cleanup processes for expired sessions
   *
   * Implementations should ensure that all session-related resources are properly
   * cleaned up and that subsequent calls to [isActive] for this session return `false`.
   *
   * @param sessionId The session identifier to terminate
   */
  suspend fun terminate(sessionId: String)
}
