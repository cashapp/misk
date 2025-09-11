package misk.mcp

import com.google.inject.multibindings.OptionalBinder
import misk.inject.KAbstractModule
import misk.inject.keyOf
import kotlin.reflect.KClass

/**
 * Guice module for installing MCP session handlers.
 *
 * This module registers an [McpSessionHandler] implementation with the dependency injection
 * framework, enabling automatic session management for MCP servers.
 *
 * ## Basic Installation
 *
 * ```kotlin
 * // Install a session handler implementation
 * install(McpSessionHandlerModule.create<MySessionHandler>())
 * ```
 *
 * ## Installation with Annotation
 *
 * ```kotlin
 * // Install with a specific annotation for multi-tenant scenarios
 * install(McpSessionHandlerModule.create<MySessionHandler>(MyServiceAnnotation::class))
 * ```
 *
 * ## Example Session Handler Implementation
 *
 * ```kotlin
 * @Singleton
 * class MySessionHandler @Inject constructor(
 *   private val redis: RedisClient
 * ) : McpSessionHandler {
 *   override suspend fun initialize(): String {
 *     val sessionId = UUID.randomUUID().toString()
 *     redis.setex("session:$sessionId", 3600, "active")
 *     return sessionId
 *   }
 *
 *   override suspend fun isActive(sessionId: String): Boolean {
 *     return redis.exists("session:$sessionId")
 *   }
 *
 *   override suspend fun terminate(sessionId: String) {
 *     redis.del("session:$sessionId")
 *   }
 * }
 * ```
 *
 * Once installed, the framework will automatically handle session lifecycle and include
 * session IDs in the "Mcp-Session-Id" response header.
 */
class McpSessionHandlerModule<T: McpSessionHandler>(
  private val mcpSessionHandlerClass: KClass<T>,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {
  override fun configure() {
    val mcpSessionHandlerKey = keyOf<McpSessionHandler>(groupAnnotationClass)
    OptionalBinder.newOptionalBinder(binder(), mcpSessionHandlerKey)
      .setBinding()
      .to(mcpSessionHandlerClass.java)
  }

  companion object {
    fun <T: McpSessionHandler> create(
      mcpSessionHandlerClass: KClass<T>,
      groupAnnotationClass: KClass<out Annotation>? = null
    ) = McpSessionHandlerModule(
      mcpSessionHandlerClass = mcpSessionHandlerClass,
      groupAnnotationClass = groupAnnotationClass
    )

    inline fun <reified T: McpSessionHandler> create(
      groupAnnotationClass: KClass<out Annotation>? = null
    ) = McpSessionHandlerModule(
      mcpSessionHandlerClass = T::class,
      groupAnnotationClass = groupAnnotationClass
    )
  }
}
