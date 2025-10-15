@file:Suppress("unused")

package misk.mcp

import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.OptionalBinder
import jakarta.inject.Inject
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.Json
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.parameterizedType
import misk.inject.setOfType
import misk.inject.toKey
import misk.inject.typeLiteral
import misk.mcp.action.McpStreamManager
import misk.mcp.config.McpConfig
import misk.mcp.internal.McpJson
import misk.mcp.internal.McpJsonRpcMessageUnmarshaller
import misk.mcp.internal.MiskMcp
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
 * and optional tool grouping via annotations.
 * 
 * The module automatically configures:
 * - Multi-binders for tools, resources, and prompts
 * - Transport layer factories for HTTP streaming and WebSocket connections
 * - JSON RPC message unmarshalling
 * - Optional session handling
 * - Metrics collection
 * 
 * Example usage:
 * ```kotlin
 * install(McpServerModule.create("my-server", mcpConfig))
 * ```
 * 
 * @see MiskMcpServer
 * @see McpConfig
 * @see McpTool
 * @see McpResource
 * @see McpPrompt
 */
@ExperimentalMiskApi
class McpServerModule private constructor(
  private val name: String,
  private val config: McpConfig,
  private val instructionsProvider: Provider<String>?,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {

  override fun configure() {
    // Set up the multi-binders for tools, resources, and prompts
    install(CommonModule())
    newMultibinder<McpPrompt>(groupAnnotationClass)
    newMultibinder<McpResource>(groupAnnotationClass)
    newMultibinder<McpTool<*>>(groupAnnotationClass)

    val serverConfig = config[name]
      ?: throw IllegalArgumentException("No MCP server configuration found for [name=$name] in [config=$config]")

    // bind the Optional McpSessionHandler and get the optional provider
    OptionalBinder.newOptionalBinder(binder(), keyOf<McpSessionHandler>(groupAnnotationClass))
    val mcpSessionHandlerProvider: Provider<Optional<McpSessionHandler>> = binder().run {
      @Suppress("UNCHECKED_CAST")
      val optionalType = parameterizedType<Optional<*>>(McpSessionHandler::class.java)
        .typeLiteral() as TypeLiteral<Optional<McpSessionHandler>>
      getProvider(optionalType.toKey(groupAnnotationClass))
    }

    // Get the providers for tools, resources, and prompts
    val promptsProvider = binder().getProvider(setOfType<McpPrompt>().toKey(groupAnnotationClass))
    val resourcesProvider = binder().getProvider(setOfType<McpResource>().toKey(groupAnnotationClass))
    val toolsProvider = binder().getProvider(setOfType<McpTool<*>>().toKey(groupAnnotationClass))

    val mcpMetricsProvider = binder().getProvider(McpMetrics::class.java)

    // Bind the factories for the transports
    bind<MiskStreamableHttpServerTransport.Factory>().toProvider(
      object : Provider<MiskStreamableHttpServerTransport.Factory> {
        @Inject
        lateinit var httpCall: ActionScoped<HttpCall>

        override fun get() =
          object : MiskStreamableHttpServerTransport.Factory {
            override fun create(sendChannel: SendChannel<ServerSentEvent>) =
              MiskStreamableHttpServerTransport(
                call = httpCall.get(),
                mcpSessionHandler = mcpSessionHandlerProvider.get().getOrNull(),
                sendChannel = sendChannel
              )
          }
      }
    )

    bind<MiskWebSocketServerTransport.Factory>().toProvider(
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

    // Create a qualified binding for the MiskMcpServer
    val serverKey = keyOf<MiskMcpServer>(groupAnnotationClass)
    bind(serverKey).toProvider {
      MiskMcpServer(
        name = name,
        config = serverConfig,
        tools = toolsProvider.get().toSet(),
        resources = resourcesProvider.get().toSet(),
        prompts = promptsProvider.get().toSet(),
        instructionsProvider = instructionsProvider,
        mcpMetrics = mcpMetricsProvider.get(),
      )
    }.asSingleton()
    val mcpServerProvider = getProvider(serverKey)

    // Create a qualified binding for the McpStreamManager
    val streamManagerKey = keyOf<McpStreamManager>(groupAnnotationClass)
    bind(streamManagerKey).toProvider(
      object : Provider<McpStreamManager> {
        @Inject
        lateinit var streamableHttpServerTransportFactory: MiskStreamableHttpServerTransport.Factory

        @Inject
        lateinit var webSocketServerTransportFactory: MiskWebSocketServerTransport.Factory
        override fun get(): McpStreamManager =
          McpStreamManager(
            streamableHttpServerTransportFactory,
            webSocketServerTransportFactory,
            mcpServer = mcpServerProvider.get()
          )
      },
    )
  }

  companion object {
    /**
     * Create an [McpServerModule] for the given [McpConfig] with an optional tool [groupAnnotation].
     * 
     * @param name The name of the MCP server configuration to use from the config
     * @param config The MCP configuration containing server settings
     * @param instructionsProvider Optional provider for server instructions/documentation
     * @param groupAnnotation Optional annotation class for grouping tools, resources, and prompts
     * @return A configured McpServerModule instance
     */
    fun create(
      name: String,
      config: McpConfig,
      instructionsProvider: Provider<String>? = null,
      groupAnnotation: KClass<out Annotation>? = null,
    ) = McpServerModule(name, config, instructionsProvider, groupAnnotation)

    /**
     * Create an [McpServerModule] with a reified group annotation type.
     * 
     * @param name The name of the MCP server configuration to use from the config
     * @param config The MCP configuration containing server settings  
     * @param instructionsProvider Optional provider for server instructions/documentation
     * @return A configured McpServerModule instance with the specified group annotation
     */
    inline fun <reified GA : Annotation> create(
      name: String,
      config: McpConfig,
      instructionsProvider: Provider<String>? = null
    ) =
      create(name, config, instructionsProvider, GA::class)

    /**
     * Create an [McpServerModule] without any group annotation.
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

      // Set up the multibinders for tools, resources, and prompts
      newMultibinder<McpPrompt>()
      newMultibinder<McpResource>()
      newMultibinder<McpTool<*>>()
    }

    /**
     * Binds the kotlin-sdk JSON RPC message deserializer
     */
    @MiskMcp
    @Provides
    fun providesMcpJson(): Json = McpJson
  }
}
