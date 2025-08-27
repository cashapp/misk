package misk.mcp

import misk.annotation.ExperimentalMiskApi
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
@ExperimentalMiskApi
class McpToolModule<T : McpTool<*>> private constructor(
  private val toolClass: KClass<T>,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP tool to the named server's tool set
    multibind<McpTool<*>>(groupAnnotationClass)
      .to(toolClass.java)
      .asSingleton()
  }

  companion object {
    fun <T : McpTool<*>> create(
      toolClass: KClass<T>,
      groupAnnotationClass: KClass<out Annotation>?
    ) = McpToolModule(
      toolClass = toolClass,
      groupAnnotationClass = groupAnnotationClass,
    )

    /**
     * @param GA The annotation type for the tool's MCP group (e.g., @AdminMCP, @PaymentsMCP).
     * @param T The type of [McpTool] implementation to register
     */
    inline fun <reified GA : Annotation, reified T : McpTool<*>> create() =
      create(
        toolClass = T::class,
        groupAnnotationClass = GA::class,
      )

    /**
     * @param T The type of [McpTool] implementation to register
     */
    @JvmName("createWithNoGroup")
    inline fun <reified T : McpTool<*>> create() =
      create(
        toolClass = T::class,
        groupAnnotationClass = null,
      )
  }
}
