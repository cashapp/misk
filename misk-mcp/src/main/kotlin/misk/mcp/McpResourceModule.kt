package misk.mcp

import misk.annotation.ExperimentalMiskApi
import misk.inject.BindingQualifier
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.qualifier
import kotlin.reflect.KClass

/**
 * Module for registering [McpResource] implementations with an MCP server.
 *
 * This module registers MCP resources that will be available through the configured MCP server.
 * Resources provide addressable data that AI models and other MCP clients can discover and retrieve.
 *
 * ## Usage
 *
 * Register resources using the reified generic create method:
 *
 * ```kotlin
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Register resources without grouping (default)
 *     install(McpResourceModule.create<DatabaseSchemaResource>())
 *     install(McpResourceModule.create<APIDocumentationResource>())
 *     install(McpResourceModule.create<ConfigurationResource>())
 *   }
 * }
 * ```
 *
 * ## Resource Grouping with BindingQualifiers
 *
 * Resources can be organized into groups using [BindingQualifier] annotations. This allows multiple
 * MCP servers to expose different sets of resources. Define a qualifier annotation and use it when
 * creating both the server and resource modules:
 *
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
 *     // Register resources to specific servers using qualifiers
 *     install(McpResourceModule.create<AdminMcp, AdminResource>())
 *     install(McpResourceModule.create<PublicMcp, PublicResource>())
 *   }
 * }
 * ```
 *
 * You can also use annotation instances for dynamic grouping:
 *
 * ```kotlin
 * val adminAnnotation = AdminMcp()
 * install(McpResourceModule.create<MyResource>(adminAnnotation))
 * ```
 *
 * ## Server Configuration
 *
 * Resources are made available through the MCP server configured via [McpServerModule].
 * All registered resources will be exposed through the server's HTTP endpoints.
 *
 * @param R The type of [McpResource] implementation to register
 * @param resourceClass The [KClass] of the resource implementation
 * @param qualifier The [BindingQualifier] used to group this resource with a specific MCP server
 *
 * @see McpResource for resource implementation details
 * @see McpServerModule for server configuration
 * @see BindingQualifier for grouping resources with servers
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpResourceModule<R : McpResource> private constructor(
  private val resourceClass: KClass<R>,
  private val qualifier: BindingQualifier?,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP resource to the named server's resource set
    multibind<McpResource>(qualifier)
      .to(resourceClass.java)
      .asSingleton()
  }

  companion object {
    /**
     * Creates an [McpResourceModule] with an optional group annotation class.
     *
     * This is the base factory method that accepts a [KClass] for both the resource
     * and the group annotation. Use the reified generic versions for more convenient
     * type-safe creation.
     *
     * @param R The type of [McpResource] implementation to register
     * @param resourceClass The [KClass] of the resource implementation
     * @param groupAnnotationClass Optional annotation class for grouping this resource with a specific MCP server
     * @return A configured McpResourceModule instance
     */
    fun <R : McpResource> create(resourceClass: KClass<R>, groupAnnotationClass: KClass<out Annotation>?) =
      McpResourceModule(
        resourceClass = resourceClass,
        qualifier = groupAnnotationClass?.qualifier,
      )

    /**
     * Creates an [McpResourceModule] with reified type parameters for both group annotation and resource.
     *
     * This is the recommended way to register resources with a specific MCP server group.
     * Both the group annotation and resource type are specified using reified generics for
     * compile-time type safety.
     *
     * Example:
     * ```kotlin
     * install(McpResourceModule.create<AdminMcp, DatabaseSchemaResource>())
     * ```
     *
     * @param GA The annotation type for the resource's MCP group (e.g., @AdminMcp, @PaymentsMcp)
     * @param R The type of [McpResource] implementation to register
     * @return A configured McpResourceModule instance
     */
    inline fun <reified GA : Annotation, reified R : McpResource> create() =
      create(
        resourceClass = R::class,
        groupAnnotationClass = GA::class,
      )

    /**
     * Creates an [McpResourceModule] without any group annotation.
     *
     * Use this when registering a resource with the default (ungrouped) MCP server.
     * The resource will be available to any MCP server that doesn't specify a group annotation.
     *
     * Example:
     * ```kotlin
     * install(McpResourceModule.create<APIDocumentationResource>())
     * ```
     *
     * @param R The type of [McpResource] implementation to register
     * @return A configured McpResourceModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    inline fun <reified R : McpResource> create() =
      create(
        resourceClass = R::class,
        groupAnnotationClass = null,
      )

    /**
     * Creates an [McpResourceModule] with an annotation instance for dynamic grouping.
     *
     * Use this when you need to create group annotations dynamically at runtime
     * rather than using compile-time annotation classes.
     *
     * @param R The type of [McpResource] implementation to register
     * @param resourceClass The [KClass] of the resource implementation
     * @param groupAnnotation Optional annotation instance for grouping this resource with a specific MCP server
     * @return A configured McpResourceModule instance
     */
    fun <R : McpResource> create(resourceClass: KClass<R>, groupAnnotation: Annotation?) =
      McpResourceModule(
        resourceClass = resourceClass,
        qualifier = groupAnnotation?.qualifier,
      )

    /**
     * Creates an [McpResourceModule] with a reified resource type and annotation instance.
     *
     * Convenience method that combines reified resource type with runtime annotation instance.
     *
     * @param R The type of [McpResource] implementation to register
     * @param groupAnnotation Optional annotation instance for grouping this resource with a specific MCP server
     * @return A configured McpResourceModule instance
     */
    inline fun <reified R : McpResource> create(groupAnnotation: Annotation?) =
      create(
        resourceClass = R::class,
        groupAnnotation = groupAnnotation,
      )
  }
}
