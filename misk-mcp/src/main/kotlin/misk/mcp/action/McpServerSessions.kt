package misk.mcp.action

import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import misk.mcp.internal.MiskServerTransport

suspend fun ServerSession.handleMessage(message: JSONRPCMessage) {
  checkNotNull((transport as? MiskServerTransport)) {
      "MiskMcpServer requires a connected MiskServerTransport to handle messages"
    }
    .handleMessage(message)
}
