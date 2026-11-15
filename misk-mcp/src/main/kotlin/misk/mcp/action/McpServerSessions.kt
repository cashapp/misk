package misk.mcp.action

import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.currentCoroutineContext
import misk.mcp.internal.McpClientConnection
import misk.mcp.internal.McpServerSession
import misk.mcp.internal.MiskServerTransport

suspend inline fun currentServerSession(): ServerSession =
  currentCoroutineContext()[McpServerSession]?.serverSession ?: error("No current ServerSession found in context")

/**
 * Returns the [ClientConnection] for the current handler invocation.
 *
 * [ClientConnection] provides handler-scoped capabilities including:
 * - [ClientConnection.sendLoggingMessage] — send log messages to the client
 * - [ClientConnection.sendResourceUpdated] — notify the client of resource changes
 * - [ClientConnection.sendToolListChanged] — notify the client that the tool list changed
 * - [ClientConnection.sendPromptListChanged] — notify the client that the prompt list changed
 * - [ClientConnection.ping] — check client connectivity
 * - [ClientConnection.sessionId] — unique session identifier
 *
 * This function must be called from within a tool, resource, or prompt handler context.
 *
 * @throws IllegalStateException if called outside a handler context
 */
suspend inline fun currentClientConnection(): ClientConnection =
  currentCoroutineContext()[McpClientConnection]?.clientConnection
    ?: error("No current ClientConnection found in context")

suspend fun ServerSession.handleMessage(message: JSONRPCMessage) {
  checkNotNull((transport as? MiskServerTransport)) {
      "MiskMcpServer requires a connected MiskServerTransport to handle messages"
    }
    .handleMessage(message)
}
