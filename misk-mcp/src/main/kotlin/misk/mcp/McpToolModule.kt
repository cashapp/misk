package misk.mcp

import misk.inject.KAbstractModule
import misk.inject.asSingleton
import kotlin.reflect.KClass

/**
 * Module for registering [McpTool] implementations with an MCP server.
 *
 * This module registers MCP tools that will be available through the configured MCP server.
 * Tools provide callable functions that AI models and other MCP clients can discover and invoke.
 *
 * ## Usage
 *
 * Register tools using the reified generic create method:
 *
 * ```kotlin
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Register tools
 *     install(McpToolModule.create<CalculatorTool>())
 *     install(McpToolModule.create<WeatherTool>())
 *     install(McpToolModule.create<DatabaseQueryTool>())
 *   }
 * }
 * ```
 *
 * ## Server Configuration
 *
 * Tools are made available through the MCP server configured via [McpServerModule].
 * All registered tools will be exposed through the server's HTTP endpoints.
 *
 * @param T The type of [McpTool] implementation to register
 * @param toolClass The [KClass] of the tool implementation
 *
 * @see McpTool for tool implementation details
 * @see McpServerModule for server configuration
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
class McpToolModule<T : McpTool<*>>(
  private val toolClass: KClass<T>,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP tool to the named server's tool set
    multibind<McpTool<*>>()
      .toProvider(getProvider(toolClass.java))
      .asSingleton()
  }

  companion object {

    /**
     * Creates an [McpToolModule] for the specified tool class.
     *
     * @param T The type of [McpTool] implementation to register
     * @param toolClass The [KClass] of the tool implementation
     * @return A configured [McpToolModule] instance
     */
    fun <T : McpTool<*>> create(toolClass: KClass<T>) =
      McpToolModule(
        toolClass = toolClass,
      )

    /**
     * Creates an [McpToolModule] using reified generics for convenient registration.
     *
     * This inline function allows you to specify the tool type without explicitly
     * passing the [KClass], making tool registration more concise and type-safe.
     *
     * @param T The type of [McpTool] implementation to register (inferred)
     * @return A configured [McpToolModule] instance
     */
    inline fun <reified T : McpTool<*>> create() =
      create(
        toolClass = T::class,
      )
  }
}
