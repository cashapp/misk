package misk.mcp

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
 * @param T The type of [McpResource] implementation to register
 * @param resourceClass The [KClass] of the resource implementation
 *
 * @see McpResource for resource implementation details
 * @see McpServerModule for server configuration
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
class McpResourceModule<T : McpResource>(
  private val resourceClass: KClass<T>,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP resource to the named server's resource set
    multibind<McpResource>()
      .toProvider(getProvider(resourceClass.java))
      .asSingleton()
  }

  companion object {

    /**
     * Creates an [McpResourceModule] for the specified resource class.
     *
     * @param T The type of [McpResource] implementation to register
     * @param resourceClass The [KClass] of the resource implementation
     * @return A configured [McpResourceModule] instance
     */
    fun <T : McpResource> create(resourceClass: KClass<T>) =
      McpResourceModule(
        resourceClass = resourceClass,
      )

    /**
     * Creates an [McpResourceModule] using reified generics for convenient registration.
     *
     * This inline function allows you to specify the resource type without explicitly
     * passing the [KClass], making resource registration more concise and type-safe.
     *
     * @param T The type of [McpResource] implementation to register (inferred)
     * @return A configured [McpResourceModule] instance
     */
    inline fun <reified T : McpResource> create() =
      create(
        resourceClass = T::class,
      )
  }
}
