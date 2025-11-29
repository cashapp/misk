@file:Suppress("unused")

package misk.mcp

import com.google.inject.Provider
import com.google.inject.TypeLiteral
import jakarta.inject.Inject
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.inject.BindingQualifier
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.inject.keyOf
import misk.inject.parameterizedType
import misk.inject.qualifier
import misk.inject.setOfType
import misk.inject.toKey
import misk.inject.typeLiteral
import misk.mcp.action.McpStreamManager
import misk.mcp.config.McpConfig
import misk.mcp.internal.McpJsonRpcMessageUnmarshaller
import misk.mcp.internal.MiskStreamableHttpServerTransport
import misk.mcp.internal.MiskWebSocketServerTransport
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.actions.WebSocket
import misk.web.marshal.Unmarshaller
import misk.web.sse.ServerSentEvent
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass


/**
 * Guice module for configuring MCP (Model Context Protocol) server functionality.
 *
 * This module sets up the necessary bindings for MCP tools, resources, prompts,
 * and transport mechanisms (WebSocket and Server-Sent Events). It provides factory
 * methods for creating configured MCP server instances with different transport options
 * and optional component grouping via [BindingQualifier] annotations.
 *
 * The module automatically configures:
 * - Multi-binders for tools, resources, and prompts
 * - Transport layer factories for HTTP streaming and WebSocket connections
 * - JSON RPC message unmarshalling
 * - Optional session handling
 * - Metrics collection
 *
 * ## Basic Usage
 *
 * Create a single MCP server without component grouping:
 *
 * ```kotlin
 * install(McpServerModule.create("my-server", mcpConfig))
 * ```
 *
 * ## Component Grouping with BindingQualifiers
 *
 * Use [BindingQualifier] annotations to create multiple MCP servers with different sets of
 * tools, resources, and prompts. This is useful for:
 * - Separating admin and public APIs
 * - Creating tenant-specific MCP servers
 * - Organizing tools by domain or service
 *
 * Define qualifier annotations:
 *
 * ```kotlin
 * @Qualifier
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class AdminMcp
 *
 * @Qualifier
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class PublicMcp
 * ```
 *
 * Create servers with qualifiers:
 *
 * ```kotlin
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Create servers with different qualifiers
 *     install(McpServerModule.create<AdminMcp>("admin_server", config.mcp))
 *     install(McpServerModule.create<PublicMcp>("public_server", config.mcp))
 *
 *     // Register components to specific servers using the same qualifiers
 *     install(McpToolModule.create<AdminMcp, AdminTool>())
 *     install(McpToolModule.create<PublicMcp, PublicTool>())
 *
 *     install(McpResourceModule.create<AdminMcp, AdminResource>())
 *     install(McpResourceModule.create<PublicMcp, PublicResource>())
 *
 *     install(McpPromptModule.create<AdminMcp, AdminPrompt>())
 *     install(McpPromptModule.create<PublicMcp, PublicPrompt>())
 *   }
 * }
 * ```
 *
 * You can also use annotation instances for dynamic grouping:
 *
 * ```kotlin
 * val adminAnnotation = AdminMcp()
 * install(McpServerModule.create("admin_server", "1.0.0", config.mcp, adminAnnotation))
 * ```
 *
 * ## Multi-Tenant Architecture
 *
 * The qualifier-based approach enables multi-tenant architectures where each tenant
 * has its own MCP server with isolated tools and resources:
 *
 * ```kotlin
 * @Qualifier annotation class TenantA
 * @Qualifier annotation class TenantB
 *
 * install(McpServerModule.create<TenantA>("tenant_a_server", config.mcp))
 * install(McpServerModule.create<TenantB>("tenant_b_server", config.mcp))
 *
 * install(McpToolModule.create<TenantA, TenantASpecificTool>())
 * install(McpToolModule.create<TenantB, TenantBSpecificTool>())
 * ```
 *
 * @param name The name of the MCP server configuration to use from the config
 * @param version Optional version string for the server (defaults to config version)
 * @param config The MCP configuration containing server settings
 * @param instructionsProvider Optional provider for server instructions/documentation
 * @param qualifier The [BindingQualifier] used to group tools, resources, and prompts with this server
 *
 * @see MiskMcpServer
 * @see McpConfig
 * @see McpToolModule
 * @see McpResourceModule
 * @see McpPromptModule
 * @see BindingQualifier
 */
@ExperimentalMiskApi
class McpServerModule private constructor(
  private val name: String,
  private val version: String?,
  private val config: McpConfig,
  private val instructionsProvider: Provider<String>?,
  private val qualifier: BindingQualifier?,
) : KAbstractModule() {

  override fun configure() {
    // Set up the multi-binders for tools, resources, and prompts
    install(CommonModule())
    newMultibinder<McpPrompt>(qualifier)
    newMultibinder<McpResource>(qualifier)
    newMultibinder<McpTool<*>>(qualifier)

    val serverConfig = config[name]
      ?: throw IllegalArgumentException("No MCP server configuration found for [name=$name] in [config=$config]")

    // bind the Optional McpSessionHandler and get the optional provider
    bindOptional(keyOf<McpSessionHandler>(qualifier))
    val mcpSessionHandlerProvider: Provider<Optional<McpSessionHandler>> = binder().run {
      @Suppress("UNCHECKED_CAST")
      val optionalType = parameterizedType<Optional<*>>(McpSessionHandler::class.java)
        .typeLiteral() as TypeLiteral<Optional<McpSessionHandler>>
      getProvider(optionalType.toKey(qualifier))
    }

    // Get the providers for tools, resources, and prompts
    val promptsProvider = binder().getProvider(setOfType<McpPrompt>().toKey(qualifier))
    val resourcesProvider = binder().getProvider(setOfType<McpResource>().toKey(qualifier))
    val toolsProvider = binder().getProvider(setOfType<McpTool<*>>().toKey(qualifier))

    val mcpMetricsProvider = binder().getProvider(McpMetrics::class.java)

    // Bind the factories for the transports
    val streamableHttpServerTransportFactoryKey = keyOf<MiskStreamableHttpServerTransport.Factory>(qualifier)
    bind(streamableHttpServerTransportFactoryKey).toProvider(
      object : Provider<MiskStreamableHttpServerTransport.Factory> {
        @Inject
        lateinit var httpCall: ActionScoped<HttpCall>

        override fun get() =
          object : MiskStreamableHttpServerTransport.Factory {
            override fun create(sendChannel: SendChannel<ServerSentEvent>) =
              MiskStreamableHttpServerTransport(
                call = httpCall.get(),
                mcpSessionHandler = mcpSessionHandlerProvider.get().getOrNull(),
                sendChannel = sendChannel,
              )
          }
      }
    )
    val streamableHttpServerTransportFactoryProvider = binder().getProvider(streamableHttpServerTransportFactoryKey)

    val webSocketServerTransportFactoryKey = keyOf<MiskWebSocketServerTransport.Factory>(qualifier)
    bind(keyOf<MiskWebSocketServerTransport.Factory>(qualifier)).toProvider(
      object : Provider<MiskWebSocketServerTransport.Factory> {
        @Inject
        lateinit var httpCall: ActionScoped<HttpCall>

        override fun get() =
          object : MiskWebSocketServerTransport.Factory {
            override fun create(webSocket: WebSocket) =
              MiskWebSocketServerTransport(
                call = httpCall.get(),
                webSocket = webSocket
              )
          }
      }
    )
    val webSocketServerTransportFactoryProvider = binder().getProvider(webSocketServerTransportFactoryKey)

    // Create a qualified binding for the MiskMcpServer
    val serverKey = keyOf<MiskMcpServer>(qualifier)
    bind(serverKey).toProvider {
      MiskMcpServer(
        name = name,
        version = version ?: serverConfig.version,
        config = serverConfig,
        tools = toolsProvider.get().toSet(),
        resources = resourcesProvider.get().toSet(),
        prompts = promptsProvider.get().toSet(),
        instructionsProvider = instructionsProvider,
        mcpMetrics = mcpMetricsProvider.get(),
      )
    }
    val mcpServerProvider = getProvider(serverKey)

    // Create a qualified binding for the McpStreamManager
    val streamManagerKey = keyOf<McpStreamManager>(qualifier)
    bind(streamManagerKey).toProvider(
      Provider {
        McpStreamManager(
          streamableHttpServerTransportFactoryProvider.get(),
          webSocketServerTransportFactoryProvider.get(),
          mcpServer = mcpServerProvider.get()
        )
      },
    )
  }

  companion object {
    /**
     * Creates an [McpServerModule] for the given [McpConfig] with an optional group annotation.
     *
     * This is the base factory method that accepts a [KClass] for the group annotation.
     * Use the reified generic versions for more convenient type-safe creation.
     *
     * @param name The name of the MCP server configuration to use from the config
     * @param config The MCP configuration containing server settings
     * @param instructionsProvider Optional provider for server instructions/documentation
     * @param groupAnnotation Optional annotation class for grouping tools, resources, and prompts
     * @param version Optional version string (defaults to version from config)
     * @return A configured McpServerModule instance
     */
    fun create(
      name: String,
      config: McpConfig,
      instructionsProvider: Provider<String>? = null,
      groupAnnotation: KClass<out Annotation>? = null,
      version: String? = null,
    ) = McpServerModule(name, version, config, instructionsProvider, groupAnnotation?.qualifier)

    /**
     * Creates an [McpServerModule] with an annotation instance for dynamic grouping.
     *
     * Use this when you need to create group annotations dynamically at runtime
     * rather than using compile-time annotation classes.
     *
     * @param name The name of the MCP server configuration to use from the config
     * @param config The MCP configuration containing server settings
     * @param groupAnnotation Annotation instance for grouping tools, resources, and prompts
     * @param instructionsProvider Optional provider for server instructions/documentation
     * @param version Optional version string (defaults to version from config)
     * @return A configured McpServerModule instance
     */
    fun create(
      name: String,
      config: McpConfig,
      groupAnnotation: Annotation,
      instructionsProvider: Provider<String>? = null,
      version: String? = null,
    ) = McpServerModule(name, version, config, instructionsProvider, groupAnnotation.qualifier)

    /**
     * Creates an [McpServerModule] with a reified group annotation type.
     *
     * This is the recommended way to create grouped MCP servers with compile-time
     * type safety. The annotation type is used to group tools, resources, and prompts
     * that should be exposed through this server.
     *
     * Example:
     * ```kotlin
     * install(McpServerModule.create<AdminMcp>("admin_server", config.mcp))
     * ```
     *
     * @param GA The annotation type for grouping (e.g., @AdminMcp, @PublicMcp)
     * @param name The name of the MCP server configuration to use from the config
     * @param config The MCP configuration containing server settings
     * @param instructionsProvider Optional provider for server instructions/documentation
     * @return A configured McpServerModule instance with the specified group annotation
     */
    inline fun <reified GA : Annotation> create(
      name: String,
      config: McpConfig,
      instructionsProvider: Provider<String>? = null
    ) = create(name, config, instructionsProvider, GA::class)

    /**
     * Creates an [McpServerModule] without any group annotation.
     *
     * Use this when you only need a single MCP server and don't need to organize
     * tools, resources, and prompts into separate groups.
     *
     * Example:
     * ```kotlin
     * install(McpServerModule.create("my_server", config.mcp))
     * ```
     *
     * @param name The name of the MCP server configuration to use from the config
     * @param config The MCP configuration containing server settings
     * @param instructionsProvider Optional provider for server instructions/documentation
     * @return A configured McpServerModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    fun create(name: String, config: McpConfig, instructionsProvider: Provider<String>? = null) =
      create(name, config, instructionsProvider, null)
  }

  private class CommonModule() : KInstallOnceModule() {
    override fun configure() {
      // Install the Unmarshaller for MCP JSON RPC messages. This uses the kotlin-sdk provided deserializer
      multibind<Unmarshaller.Factory>().to<McpJsonRpcMessageUnmarshaller.Factory>()
    }
  }
}
