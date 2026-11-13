package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that holds an MCP server session.
 *
 * This class wraps a [ServerSession] from the MCP Kotlin SDK and makes it available in the coroutine context for use
 * across the request lifecycle. It allows access to session-specific functionality and state during MCP tool, resource,
 * and prompt handling.
 *
 * @param serverSession The underlying MCP server session from the Kotlin SDK
 */
data class McpServerSession(val serverSession: ServerSession) : AbstractCoroutineContextElement(McpServerSession) {
  /** Key for [McpServerSession] instance in the coroutine context. */
  companion object Key : CoroutineContext.Key<McpServerSession>

  /** Returns a string representation of the object. */
  override fun toString(): String = "McpServerSession"
}

/**
 * Coroutine context element that holds an MCP [ClientConnection].
 *
 * This class wraps a [ClientConnection] from the MCP Kotlin SDK and makes it available in the coroutine context during
 * tool, resource, and prompt handler execution. [ClientConnection] provides handler-scoped capabilities such as
 * [ClientConnection.sendLoggingMessage], [ClientConnection.sendResourceUpdated], and [ClientConnection.ping].
 *
 * @param clientConnection The underlying MCP client connection from the Kotlin SDK
 */
data class McpClientConnection(val clientConnection: ClientConnection) :
  AbstractCoroutineContextElement(McpClientConnection) {
  /** Key for [McpClientConnection] instance in the coroutine context. */
  companion object Key : CoroutineContext.Key<McpClientConnection>

  /** Returns a string representation of the object. */
  override fun toString(): String = "McpClientConnection"
}
