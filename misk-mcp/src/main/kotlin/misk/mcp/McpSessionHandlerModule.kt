package misk.mcp

import com.google.inject.multibindings.OptionalBinder
import misk.inject.KAbstractModule
import misk.inject.keyOf
import kotlin.reflect.KClass

/**
 * Guice module for installing MCP session handlers for StreamableHTTP transport.
 *
 * This module registers an [McpSessionHandler] implementation with the dependency injection
 * framework, enabling automatic session management for MCP servers using StreamableHTTP
 * transport (StreamableHTTP-based communication via `@McpPost`, `@McpGet`, and `@McpDelete` endpoints).
 *
 * ## Transport Compatibility
 *
 * **StreamableHTTP Transport Only**: This module is designed exclusively for StreamableHTTP
 * transport using Server-Sent Events. Session handlers are not used with WebSocket transport
 * (`@McpWebSocket`), which maintains connection state through the persistent WebSocket
 * connection itself.
 *
 * ## Basic Installation
 *
 * ```kotlin
 * // Install a session handler implementation for StreamableHTTP transport
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
 * Once installed, the framework will automatically handle session lifecycle for StreamableHTTP
 * requests and include session IDs in the "Mcp-Session-Id" response header.
 *
 * @see McpSessionHandler for session management interface
 * @see misk.mcp.action.McpPost for StreamableHTTP request handling
 * @see misk.mcp.action.McpGet for StreamableHTTP event streaming
 * @see misk.mcp.action.McpDelete for StreamableHTTP session termination
 */
class McpSessionHandlerModule<T: McpSessionHandler>(
  private val mcpSessionHandlerClass: KClass<T>,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {
  override fun configure() {
    val mcpSessionHandlerKey = keyOf<McpSessionHandler>(groupAnnotationClass)
    bindOptionalBinding(mcpSessionHandlerKey).to(mcpSessionHandlerClass.java)
  }

  companion object {
    /**
     * Creates an [McpSessionHandlerModule] with an optional group annotation class.
     *
     * This is the base factory method that accepts a [KClass] for both the session handler
     * and the group annotation. Use the reified generic version for more convenient
     * type-safe creation.
     *
     * @param T The type of [McpSessionHandler] implementation to register
     * @param mcpSessionHandlerClass The [KClass] of the session handler implementation
     * @param groupAnnotationClass Optional annotation class for grouping this handler with a specific MCP server
     * @return A configured McpSessionHandlerModule instance
     */
    fun <T: McpSessionHandler> create(
      mcpSessionHandlerClass: KClass<T>,
      groupAnnotationClass: KClass<out Annotation>? = null
    ) = McpSessionHandlerModule(
      mcpSessionHandlerClass = mcpSessionHandlerClass,
      groupAnnotationClass = groupAnnotationClass
    )

    /**
     * Creates an [McpSessionHandlerModule] with a reified session handler type.
     *
     * This is the recommended way to register session handlers. The handler type is
     * specified using reified generics for compile-time type safety.
     *
     * Example without grouping:
     * ```kotlin
     * install(McpSessionHandlerModule.create<MySessionHandler>())
     * ```
     *
     * Example with grouping:
     * ```kotlin
     * install(McpSessionHandlerModule.create<MySessionHandler>(AdminMcp::class))
     * ```
     *
     * @param T The type of [McpSessionHandler] implementation to register
     * @param groupAnnotationClass Optional annotation class for grouping this handler with a specific MCP server
     * @return A configured McpSessionHandlerModule instance
     */
    inline fun <reified T: McpSessionHandler> create(
      groupAnnotationClass: KClass<out Annotation>? = null
    ) = McpSessionHandlerModule(
      mcpSessionHandlerClass = T::class,
      groupAnnotationClass = groupAnnotationClass
    )
  }
}
