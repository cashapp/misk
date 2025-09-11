package misk.mcp

import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.OptionalBinder
import jakarta.inject.Inject
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
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.marshal.Unmarshaller
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass

@ExperimentalMiskApi
class McpServerModule private constructor(
  private val name: String,
  private val config: McpConfig,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {

  override fun configure() {
    // Set up the multibinders for tools, resources, and prompts
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

    // If there is only one configured server, bind without a name qualifier
    val serverKey = keyOf<MiskMcpServer>(groupAnnotationClass)
    val serverProvider = Provider {
      MiskMcpServer(
        name = name,
        config = serverConfig,
        mcpSessionHandler = mcpSessionHandlerProvider.get().getOrNull(),
        tools = toolsProvider.get().toSet(),
        resources = resourcesProvider.get().toSet(),
        prompts = promptsProvider.get().toSet(),
      )
    }
    bind(serverKey).toProvider(serverProvider).asSingleton()

    val streamManagerKey = keyOf<McpStreamManager>(groupAnnotationClass)
    bind(streamManagerKey).toProvider(
      object : Provider<McpStreamManager> {
        @Inject
        lateinit var httpCall: ActionScoped<HttpCall>
        override fun get(): McpStreamManager =
          McpStreamManager(httpCall = httpCall, mcpServerProvider = serverProvider)
      },
    ).asSingleton()
  }

  companion object {
    /**
     * Create an [McpServerModule] for the given [McpConfig] with an optional tool [groupAnnotation].
     */
    fun create(
      name: String,
      config: McpConfig,
      groupAnnotation: KClass<out Annotation>? = null,
    ) = McpServerModule(name, config, groupAnnotation)

    inline fun <reified GA : Annotation> create(name: String, config: McpConfig) = create(name, config, GA::class)

    @JvmName("createWithNoGroup")
    fun create(name: String, config: McpConfig) = create(name, config, null)
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
