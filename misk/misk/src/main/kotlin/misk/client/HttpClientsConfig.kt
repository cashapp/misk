package misk.client

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.type.TypeFactory
import misk.config.Config
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import java.io.File
import java.time.Duration

data class BackwardsCompatibleEndpointConfig(
    //Common fields
  val url: String? = null,
  val envoy: HttpClientEnvoyConfig? = null,

    //Legacy fields
  val connectTimeout: Duration? = null,
  val writeTimeout: Duration? = null,
  val readTimeout: Duration? = null,
  val pingInterval: Duration? = null,
  val callTimeout: Duration? = null,
  val maxRequests: Int = 128,
  val maxRequestsPerHost: Int = 32,
  val maxIdleConnections: Int = 100,
  val keepAliveDuration: Duration = Duration.ofMinutes(5),
  val ssl: HttpClientSSLConfig? = null,

    //New fields
  val clientConfig: HttpClientConfig? = null
)

class BackwardsCompatibleEndpointConfigConverter : com.fasterxml.jackson.databind.util.Converter<BackwardsCompatibleEndpointConfig, HttpClientEndpointConfig> {
  override fun getInputType(typeFactory: TypeFactory) =
      typeFactory.constructType(BackwardsCompatibleEndpointConfig::class.java)

  override fun getOutputType(typeFactory: TypeFactory) =
      typeFactory.constructType(HttpClientEndpointConfig::class.java)

  override fun convert(value: BackwardsCompatibleEndpointConfig) =
      //http://%s.%s.gns.square
      if (value.clientConfig == null)
        HttpClientEndpointConfig(
            url = value.url,
            envoy = value.envoy,
            clientConfig = HttpClientConfig(
                value.connectTimeout,
                value.writeTimeout,
                value.readTimeout,
                value.pingInterval,
                value.callTimeout,
                value.maxRequests,
                value.maxRequestsPerHost,
                value.maxIdleConnections,
                value.keepAliveDuration,
                value.ssl
            )
        )
      else
        HttpClientEndpointConfig(
            url = value.url,
            envoy = value.envoy,
            clientConfig = value.clientConfig
        )
}

data class HttpClientsConfig(
  val defaultConnectTimeout: Duration? = null,
  val defaultWriteTimeout: Duration? = null,
  val defaultReadTimeout: Duration? = null,
  val ssl: HttpClientSSLConfig? = null,
  val defaultPingInterval: Duration? = null,
  val defaultCallTimeout: Duration? = null,
  @JsonDeserialize(contentConverter = BackwardsCompatibleEndpointConfigConverter::class)
  val endpoints: Map<String, HttpClientEndpointConfig> = mapOf()
) : Config {
  /** @return The [HttpClientEndpointConfig] for the given client, populated with defaults as needed */
  operator fun get(clientName: String): HttpClientEndpointConfig {
    // TODO(mmihic): Cache, proxy, etc
    val endpointConfig = endpoints[clientName] ?: throw IllegalArgumentException(
        "no client configuration for endpoint $clientName")

    val clientConfig = endpointConfig.clientConfig
    return HttpClientEndpointConfig(
        url = endpointConfig.url,
        envoy = endpointConfig.envoy,
        clientConfig = clientConfig.copy(
            connectTimeout = clientConfig.connectTimeout ?: defaultConnectTimeout,
            writeTimeout = clientConfig.writeTimeout ?: defaultWriteTimeout,
            readTimeout = clientConfig.readTimeout ?: defaultReadTimeout,
            pingInterval = clientConfig.pingInterval ?: defaultPingInterval,
            callTimeout = clientConfig.callTimeout ?: defaultCallTimeout,
            ssl = clientConfig.ssl ?: ssl
        )
    )
  }

  /**
   * Returns a default config with no url/envoy config specified.
   * Used to build up endpoint config for clients dynamically.
   */
  fun getDefault(): HttpClientEndpointConfig {
    return HttpClientEndpointConfig(
        url = null,
        envoy = null,
        clientConfig = HttpClientConfig(
            connectTimeout = defaultConnectTimeout,
            writeTimeout = defaultWriteTimeout,
            readTimeout = defaultReadTimeout,
            pingInterval = defaultPingInterval,
            callTimeout = defaultCallTimeout,
            ssl = ssl
        )
    )
  }
}

data class HttpClientSSLConfig(
  val cert_store: CertStoreConfig?,
  val trust_store: TrustStoreConfig
)

data class HttpClientConfig(
  val connectTimeout: Duration? = null,
  val writeTimeout: Duration? = null,
  val readTimeout: Duration? = null,
  val pingInterval: Duration? = null,
  val callTimeout: Duration? = null,
  val maxRequests: Int = 128,
  val maxRequestsPerHost: Int = 32,
  val maxIdleConnections: Int = 100,
  val keepAliveDuration: Duration = Duration.ofMinutes(5),
  val ssl: HttpClientSSLConfig? = null,
  val unixSocketFile: File? = null,
  val protocols: List<String>? = null
)

@Deprecated("Use default constructor")
fun HttpClientEndpointConfig(
  url: String? = null,
  envoy: HttpClientEnvoyConfig? = null,
  connectTimeout: Duration? = null,
  writeTimeout: Duration? = null,
  readTimeout: Duration? = null,
  pingInterval: Duration? = null,
  callTimeout: Duration? = null,
  maxRequests: Int = 128,
  maxRequestsPerHost: Int = 32,
  maxIdleConnections: Int = 100,
  keepAliveDuration: Duration = Duration.ofMinutes(5),
  ssl: HttpClientSSLConfig? = null
) = HttpClientEndpointConfig(
    url = url,
    envoy = envoy,
    clientConfig = HttpClientConfig(
        connectTimeout,
        writeTimeout,
        readTimeout,
        pingInterval,
        callTimeout,
        maxRequests,
        maxRequestsPerHost,
        maxIdleConnections,
        keepAliveDuration,
        ssl
    )
)

data class HttpClientEndpointConfig(
  val url: String? = null,
  val envoy: HttpClientEnvoyConfig? = null,
  val clientConfig: HttpClientConfig = HttpClientConfig()
) {
  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.connectTimeout"))
  val connectTimeout
    get() = clientConfig.connectTimeout

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.writeTimeout"))
  val writeTimeout
    get() = clientConfig.writeTimeout

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.readTimeout"))
  val readTimeout
    get() = clientConfig.readTimeout

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.pingInterval"))
  val pingInterval
    get() = clientConfig.pingInterval

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.callTimeout"))
  val callTimeout
    get() = clientConfig.callTimeout

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.maxRequests"))
  val maxRequests
    get() = clientConfig.maxRequests

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.maxRequestsPerHost"))
  val maxRequestsPerHost
    get() = clientConfig.maxRequestsPerHost

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.maxIdleConnections"))
  val maxIdleConnections
    get() = clientConfig.maxIdleConnections

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.keepAliveDuration"))
  val keepAliveDuration
    get() = clientConfig.keepAliveDuration

  @Deprecated(
      "Use clientConfig property",
      replaceWith = ReplaceWith("clientConfig.ssl"))
  val ssl
    get() = clientConfig.ssl
}

data class HttpClientEnvoyConfig(
  val app: String,

  /** Environment to target. If null, the same environment as the app is running in is assumed. */
  val env: String? = null
)
