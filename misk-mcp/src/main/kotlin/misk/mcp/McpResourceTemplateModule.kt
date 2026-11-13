package misk.mcp

import kotlin.reflect.KClass
import misk.annotation.ExperimentalMiskApi
import misk.inject.BindingQualifier
import misk.inject.KAbstractModule
import misk.inject.qualifier

/**
 * Module for registering [McpResourceTemplate] implementations with an MCP server.
 *
 * This module registers MCP resource templates that will be available through the configured MCP server. Resource
 * templates provide parameterized access to families of resources using URI templates.
 *
 * ## Usage
 *
 * Register resource templates using the reified generic create method:
 * ```kotlin
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     install(McpResourceTemplateModule.create<TableSchemaResource>())
 *     install(McpResourceTemplateModule.create<LogAccessResource>())
 *   }
 * }
 * ```
 *
 * ## Resource Template Grouping with BindingQualifiers
 *
 * Resource templates can be organized into groups using [BindingQualifier] annotations. This allows multiple MCP servers
 * to expose different sets of resource templates:
 * ```kotlin
 * install(McpResourceTemplateModule.create<AdminMcp, AdminSchemaResource>())
 * install(McpResourceTemplateModule.create<PublicMcp, PublicSchemaResource>())
 * ```
 *
 * @param RT The type of [McpResourceTemplate] implementation to register
 * @param resourceTemplateClass The [KClass] of the resource template implementation
 * @param qualifier The [BindingQualifier] used to group this resource template with a specific MCP server
 * @see McpResourceTemplate for resource template implementation details
 * @see McpServerModule for server configuration
 * @see McpResourceModule for static (non-parameterized) resources
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpResourceTemplateModule<RT : McpResourceTemplate>
private constructor(
  private val resourceTemplateClass: KClass<RT>,
  private val qualifier: BindingQualifier?,
) : KAbstractModule() {

  override fun configure() {
    multibind<McpResourceTemplate>(qualifier).to(resourceTemplateClass.java)
  }

  companion object {
    /**
     * Creates an [McpResourceTemplateModule] with an optional group annotation class.
     *
     * This is the base factory method that accepts a [KClass] for both the resource template and the group annotation.
     * Use the reified generic versions for more convenient type-safe creation.
     *
     * @param RT The type of [McpResourceTemplate] implementation to register
     * @param resourceTemplateClass The [KClass] of the resource template implementation
     * @param groupAnnotationClass Optional annotation class for grouping this resource template with a specific MCP server
     * @return A configured McpResourceTemplateModule instance
     */
    fun <RT : McpResourceTemplate> create(
      resourceTemplateClass: KClass<RT>,
      groupAnnotationClass: KClass<out Annotation>?,
    ) = McpResourceTemplateModule(resourceTemplateClass = resourceTemplateClass, qualifier = groupAnnotationClass?.qualifier)

    /**
     * Creates an [McpResourceTemplateModule] with reified type parameters for both group annotation and resource
     * template.
     *
     * This is the recommended way to register resource templates with a specific MCP server group.
     *
     * Example:
     * ```kotlin
     * install(McpResourceTemplateModule.create<AdminMcp, TableSchemaResource>())
     * ```
     *
     * @param GA The annotation type for the resource template's MCP group
     * @param RT The type of [McpResourceTemplate] implementation to register
     * @return A configured McpResourceTemplateModule instance
     */
    inline fun <reified GA : Annotation, reified RT : McpResourceTemplate> create() =
      create(resourceTemplateClass = RT::class, groupAnnotationClass = GA::class)

    /**
     * Creates an [McpResourceTemplateModule] without any group annotation.
     *
     * Use this when registering a resource template with the default (ungrouped) MCP server.
     *
     * Example:
     * ```kotlin
     * install(McpResourceTemplateModule.create<TableSchemaResource>())
     * ```
     *
     * @param RT The type of [McpResourceTemplate] implementation to register
     * @return A configured McpResourceTemplateModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    inline fun <reified RT : McpResourceTemplate> create() =
      create(resourceTemplateClass = RT::class, groupAnnotationClass = null)

    /**
     * Creates an [McpResourceTemplateModule] with an annotation instance for dynamic grouping.
     *
     * Use this when you need to create group annotations dynamically at runtime rather than using compile-time
     * annotation classes.
     *
     * @param RT The type of [McpResourceTemplate] implementation to register
     * @param resourceTemplateClass The [KClass] of the resource template implementation
     * @param groupAnnotation Optional annotation instance for grouping this resource template with a specific MCP server
     * @return A configured McpResourceTemplateModule instance
     */
    fun <RT : McpResourceTemplate> create(
      resourceTemplateClass: KClass<RT>,
      groupAnnotation: Annotation?,
    ) = McpResourceTemplateModule(resourceTemplateClass = resourceTemplateClass, qualifier = groupAnnotation?.qualifier)

    /**
     * Creates an [McpResourceTemplateModule] with a reified resource template type and annotation instance.
     *
     * Convenience method that combines reified resource template type with runtime annotation instance.
     *
     * @param RT The type of [McpResourceTemplate] implementation to register
     * @param groupAnnotation Optional annotation instance for grouping this resource template with a specific MCP server
     * @return A configured McpResourceTemplateModule instance
     */
    inline fun <reified RT : McpResourceTemplate> create(groupAnnotation: Annotation?) =
      create(resourceTemplateClass = RT::class, groupAnnotation = groupAnnotation)
  }
}
