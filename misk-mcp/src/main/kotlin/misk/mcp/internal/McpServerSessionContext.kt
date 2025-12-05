package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that holds an MCP server session.
 *
 * This class wraps a [ServerSession] from the MCP Kotlin SDK and makes it available
 * in the coroutine context for use across the request lifecycle. It allows access
 * to session-specific functionality and state during MCP tool, resource, and prompt
 * handling.
 *
 * @param serverSession The underlying MCP server session from the Kotlin SDK
 */
data class McpServerSession(
  val serverSession: ServerSession,
) : AbstractCoroutineContextElement(McpServerSession) {
  /**
   * Key for [McpServerSession] instance in the coroutine context.
   */
  companion object Key : CoroutineContext.Key<McpServerSession>

  /**
   * Returns a string representation of the object.
   */
  override fun toString(): String = "McpServerSession"
}