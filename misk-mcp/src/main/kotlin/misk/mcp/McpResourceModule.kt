package misk.mcp

import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.inject.asSingleton
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
 *     // Register resources
 *     install(McpResourceModule.create<DatabaseSchemaResource>())
 *     install(McpResourceModule.create<APIDocumentationResource>())
 *     install(McpResourceModule.create<ConfigurationResource>())
 *   }
 * }
 * ```
 *
 * ## Server Configuration
 *
 * Resources are made available through the MCP server configured via [McpServerModule].
 * All registered resources will be exposed through the server's HTTP endpoints.
 *
 * @param R The type of [McpResource] implementation to register
 * @param resourceClass The [KClass] of the resource implementation
 *
 * @see McpResource for resource implementation details
 * @see McpServerModule for server configuration
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpResourceModule<R : McpResource> private constructor(
  private val resourceClass: KClass<R>,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP resource to the named server's resource set
    multibind<McpResource>(groupAnnotationClass)
      .to(resourceClass.java)
      .asSingleton()
  }

  companion object {
    fun <R : McpResource> create(resourceClass: KClass<R>, groupAnnotationClass: KClass<out Annotation>?) =
      McpResourceModule(
        resourceClass = resourceClass,
        groupAnnotationClass = groupAnnotationClass,
      )

    /**
     * @param GA The annotation type for the tool's MCP group (e.g., @AdminMCP, @PaymentsMCP).
     * @param R The type of [McpResource] implementation to register
     */
    inline fun <reified GA : Annotation, reified R : McpResource> create() =
      create(
        resourceClass = R::class,
        groupAnnotationClass = GA::class,
      )

    /**
     * @param R The type of [McpResource] implementation to register
     */
    @JvmName("createWithNoGroup")
    inline fun <reified R : McpResource> create() =
      create(
        resourceClass = R::class,
        groupAnnotationClass = null,
      )
  }
}
