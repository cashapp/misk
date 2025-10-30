package misk.mcp

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class InMemoryMcpSessionHandler @Inject constructor() : McpSessionHandler {
  private val activeSessions = ConcurrentHashMap.newKeySet<String>()

  override suspend fun initialize(): String {
    val sessionId = UUID.randomUUID().toString()
    activeSessions.add(sessionId)
    return sessionId
  }

  override suspend fun isActive(sessionId: String): Boolean {
    return activeSessions.contains(sessionId)
  }

  override suspend fun terminate(sessionId: String) {
    activeSessions.remove(sessionId)
  }
}
