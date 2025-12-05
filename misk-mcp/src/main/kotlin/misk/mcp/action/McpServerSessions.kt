package misk.mcp.action

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlinx.coroutines.currentCoroutineContext
import misk.mcp.internal.McpServerSession
import misk.mcp.internal.MiskServerTransport

suspend inline fun currentServerSession(): ServerSession =
  currentCoroutineContext()[McpServerSession]?.serverSession
    ?: error("No current ServerSession found in context")


suspend fun ServerSession.handleMessage(message: JSONRPCMessage) {
  checkNotNull((transport as? MiskServerTransport)) {
    "MiskMcpServer requires a connected MiskServerTransport to handle messages"
  }.handleMessage(message)
}