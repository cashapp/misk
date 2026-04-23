package misk.mcp.internal

import misk.mcp.McpSessionHandler

class DefaultSessionHandler : McpSessionHandler {
  override suspend fun initialize(): String {
    TODO("Not yet implemented")
  }

  override suspend fun isActive(sessionId: String): Boolean {
    TODO("Not yet implemented")
  }

  override suspend fun terminate(sessionId: String) {
    TODO("Not yet implemented")
  }
}