package misk.mcp

import com.google.inject.Provides
import kotlinx.serialization.json.Json
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.mcp.config.McpConfig
import misk.mcp.internal.McpJson
import misk.mcp.internal.McpJsonRpcMessageUnmarshaller
import misk.mcp.internal.MiskMcp
import misk.web.marshal.Unmarshaller

class McpServerModule(
  private val config: McpConfig,
) : KAbstractModule() {
  override fun configure() {
    val (serverName, serverConfig) = requireNotNull(config.entries.singleOrNull()) {
      "McpConfig must contain only one server configuration"
    }

    // Install the Unmarshaller for MCP JSON RPC messages. This uses the kotlin-sdk provided deserializer
    multibind<Unmarshaller.Factory>().to<McpJsonRpcMessageUnmarshaller.Factory>()

    // Set up the multibinders for tools, resources, and prompts
    newMultibinder<McpPrompt>()
    newMultibinder<McpResource>()
    newMultibinder<McpTool<*>>()

    // Get the providers for tools, resources, and prompts
    val promptsProvider = binder().getProvider(setOfType<McpPrompt>().toKey())
    val resourcesProvider = binder().getProvider(setOfType<McpResource>().toKey())
    val toolsProvider = binder().getProvider(setOfType<McpTool<*>>().toKey())

    // If there is only one configured server, bind without a name qualifier
    val serverKey = keyOf<MiskMcpServer>()
    bind(serverKey).toProvider {
      MiskMcpServer(
        name = serverName,
        config = serverConfig,
        tools = toolsProvider.get(),
        resources = resourcesProvider.get(),
        prompts = promptsProvider.get(),
      )
    }
  }


  /**
   * Binds the kotlin-sdk JSON RPC message deserializer
   */
  @MiskMcp
  @Provides
  fun providesMcpJson(): Json = McpJson

  companion object {

    @JvmStatic
    fun create(
      config: McpConfig,
    ) = McpServerModule(config)
  }
}
