package misk.mcp

import kotlin.reflect.KClass
import misk.annotation.ExperimentalMiskApi
import misk.inject.BindingQualifier
import misk.inject.KAbstractModule
import misk.inject.qualifier

/**
 * Module for registering [McpTool] implementations with an MCP server.
 *
 * This module registers MCP tools that will be available through the configured MCP server. Tools provide callable
 * functions that AI models and other MCP clients can discover and invoke.
 *
 * ## Usage
 *
 * Register tools using the reified generic create method:
 * ```kotlin
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Register tools without grouping (default)
 *     install(McpToolModule.create<CalculatorTool>())
 *     install(McpToolModule.create<WeatherTool>())
 *     install(McpToolModule.create<DatabaseQueryTool>())
 *   }
 * }
 * ```
 *
 * ## Tool Grouping with BindingQualifiers
 *
 * Tools can be organized into groups using [BindingQualifier] annotations. This allows multiple MCP servers to expose
 * different sets of tools. Define a qualifier annotation and use it when creating both the server and tool modules:
 * ```kotlin
 * @Qualifier
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class AdminMcp
 *
 * @Qualifier
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class PublicMcp
 *
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Create servers with different qualifiers
 *     install(McpServerModule.create<AdminMcp>("admin_server", config.mcp))
 *     install(McpServerModule.create<PublicMcp>("public_server", config.mcp))
 *
 *     // Register tools to specific servers using qualifiers
 *     install(McpToolModule.create<AdminMcp, AdminTool>())
 *     install(McpToolModule.create<PublicMcp, PublicTool>())
 *   }
 * }
 * ```
 *
 * You can also use annotation instances for dynamic grouping:
 * ```kotlin
 * val adminAnnotation = AdminMcp()
 * install(McpToolModule.create<MyTool>(adminAnnotation))
 * ```
 *
 * ## Server Configuration
 *
 * Tools are made available through the MCP server configured via [McpServerModule]. All registered tools will be
 * exposed through the server's HTTP endpoints.
 *
 * @param T The type of [McpTool] implementation to register
 * @param toolClass The [KClass] of the tool implementation
 * @param qualifier The [BindingQualifier] used to group this tool with a specific MCP server
 * @see McpTool for tool implementation details
 * @see McpServerModule for server configuration
 * @see BindingQualifier for grouping tools with servers
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpToolModule<T : McpTool<*>>
private constructor(private val toolClass: KClass<T>, private val qualifier: BindingQualifier?) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP tool to the named server's tool set
    multibind<McpTool<*>>(qualifier).to(toolClass.java)
  }

  companion object {
    /**
     * Creates an [McpToolModule] without any group annotation.
     *
     * Use this when registering a tool with the default (ungrouped) MCP server.
     * The tool will be available to any MCP server that doesn't specify a group annotation.
     *
     * Example:
     * ```kotlin
     * install(McpToolModule.create(CalculatorTool::class))
     * ```
     *
     * @param T The type of [McpTool] implementation to register
     * @param toolClass The [KClass] of the tool implementation
     * @return A configured McpToolModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    fun <T : McpTool<*>> create(
      toolClass: KClass<T>,
    ) = McpToolModule(toolClass, null)

    /**
     * Creates an [McpToolModule] without any group annotation.
     *
     * Use this when registering a tool with the default (ungrouped) MCP server.
     * The tool will be available to any MCP server that doesn't specify a group annotation.
     *
     * Example:
     * ```kotlin
     * install(McpToolModule.create<CalculatorTool>())
     * ```
     *
     * @param T The type of [McpTool] implementation to register
     * @return A configured McpToolModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    inline fun <reified T : McpTool<*>> create() =
      create( T::class)

    /**
     * Creates an [McpToolModule] with an optional group annotation class.
     *
     * This is the base factory method that accepts a [KClass] for both the tool and the group annotation. Use the
     * reified generic versions for more convenient type-safe creation.
     *
     * @param T The type of [McpTool] implementation to register
     * @param toolClass The [KClass] of the tool implementation
     * @param groupAnnotationClass Optional annotation class for grouping this tool with a specific MCP server
     * @return A configured McpToolModule instance
     */
    fun <T : McpTool<*>> create(toolClass: KClass<T>, groupAnnotationClass: KClass<out Annotation>?) =
      McpToolModule( toolClass,  groupAnnotationClass?.qualifier)

    /**
     * Creates an [McpToolModule] with a reified tool type and optional group annotation class.
     *
     * Convenience method that combines a reified tool type with a group annotation class parameter.
     *
     * Example:
     * ```kotlin
     * install(McpToolModule.create<AdminTool>(AdminMcp::class))
     * ```
     *
     * @param T The type of [McpTool] implementation to register
     * @param groupAnnotationClass Optional annotation class for grouping this tool with a specific MCP server
     * @return A configured McpToolModule instance
     */
    inline fun <reified T : McpTool<*>> create(groupAnnotationClass: KClass<out Annotation>?) =
      create( T::class,  groupAnnotationClass)


    /**
     * Creates an [McpToolModule] with an annotation instance for dynamic grouping.
     *
     * Use this when you need to create group annotations dynamically at runtime rather than using compile-time
     * annotation classes.
     *
     * @param T The type of [McpTool] implementation to register
     * @param toolClass The [KClass] of the tool implementation
     * @param groupAnnotation Optional annotation instance for grouping this tool with a specific MCP server
     * @return A configured McpToolModule instance
     */
    fun <T : McpTool<*>> create(toolClass: KClass<T>, groupAnnotation: Annotation?) =
      McpToolModule( toolClass,  groupAnnotation?.qualifier)

    /**
     * Creates an [McpToolModule] with a reified tool type and annotation instance.
     *
     * Convenience method that combines reified tool type with runtime annotation instance.
     *
     * @param T The type of [McpTool] implementation to register
     * @param groupAnnotation Optional annotation instance for grouping this tool with a specific MCP server
     * @return A configured McpToolModule instance
     */
    inline fun <reified T : McpTool<*>> create(groupAnnotation: Annotation?) =
      create( T::class,  groupAnnotation)
  }
}
