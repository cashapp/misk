package misk.mcp.client

import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.name.Names
import com.squareup.wire.Service
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.inject.keyOf
import misk.mcp.client.config.McpClientConfig
import misk.mcp.client.config.McpTransport
import misk.web.marshal.Unmarshaller
import okhttp3.OkHttpClient
import kotlin.reflect.KClass

/**
 * Guice module for MCP client functionality.
 * 
 * Installs the necessary components for MCP client operations including:
 * - Client manager for handling multiple server connections
 * - Individual client instances for each configured server
 * - Configuration binding
 */
class McpClientModule<T : Client> private constructor(
  private val kclass: KClass<T>,
  private val serviceName: String,
  private val serverName: String,
  private val annotation: Annotation? = null,
  private val config: McpClientConfig
) : KAbstractModule() {

  private val httpClientAnnotation = annotation ?: Names.named(kclass.qualifiedName)
  
  override fun configure() {
    install(CommonModule(config))
    install(HttpClientModule(serviceName, httpClientAnnotation))
    val httpClientProvider = binder().getProvider(keyOf<OkHttpClient>(httpClientAnnotation))

    // Use requestInjection() to check that our configuration exists when the injector is created.
    requestInjection(object : Any() {
      @Inject fun injectHttpClientsConfig(httpClientsConfig: HttpClientsConfig) {
        require(serviceName in httpClientsConfig.endpointNames()) {
          "No HTTP endpoint configured for '$serviceName'... update your yaml to include it?"
        }
      }
    })

    // Use requestInjection() to check that our configuration exists when the injector is created.
    requestInjection(object : Any() {
      @Inject fun injectHttpClientsConfig(httpClientsConfig: HttpClientsConfig) {
        require(serverName in httpClientsConfig.endpointNames()) {
          "No MCP server configured for '$serverName'... update your yaml to include it?"
        }
      }
    })

  }

  private class StreamableHttpClientTransport : Provider<StreamableHttpClientTransport> {
    @Inject lateinit var mcpClientConfigProvider: Provider<McpClientConfig>
    @Inject lateinit var httpClientsConfigProvider: Provider<HttpClientsConfig>


    override fun get(): McpClientModule.StreamableHttpClientTransport {
      TODO("Not yet implemented")
    }

  }

  private class CommonModule(
    private val config: McpClientConfig
  ) : KInstallOnceModule() {
    override fun configure() {
      bind<McpClientConfig>().toInstance(config)
    }
  }



  companion object {
    /**
     * Create an MCP client module with the given configuration.
     * 
     * @param config MCP client configuration
     * @return Configured module instance
     */
    @JvmStatic
    fun create(config: McpClientConfig): McpClientModule {
      return McpClientModule(config)
    }
  }
}

