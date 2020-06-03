package misk.client

import misk.config.Config
import misk.endpoints.HttpClientConfig
import misk.endpoints.HttpClientEndpointConfig
import misk.endpoints.HttpClientSSLConfig
import java.time.Duration

data class HttpClientsConfig(
  private val endpoints: Map<String, HttpClientEndpointConfig> = mapOf(),
  private val defaultClientConfig: HttpClientConfig = HttpClientConfig()
) : Config {

  constructor(
    vararg endpoints: Pair<String, HttpClientEndpointConfig>,
    defaultClientConfig: HttpClientConfig = HttpClientConfig()
  ) : this(endpoints.toMap(), defaultClientConfig)

  @Deprecated(
      "Kept for backwards compatibility",
      replaceWith = ReplaceWith("::HttpClientsConfig(defaultClientConfig, endpoints)")
  )
  constructor(
    defaultConnectTimeout: Duration? = null,
    defaultWriteTimeout: Duration? = null,
    defaultReadTimeout: Duration? = null,
    ssl: HttpClientSSLConfig? = null,
    defaultPingInterval: Duration? = null,
    defaultCallTimeout: Duration? = null,
    endpoints: Map<String, HttpClientEndpointConfig> = mapOf()
  ) : this(
      endpoints,
      HttpClientConfig(
          connectTimeout = defaultConnectTimeout,
          writeTimeout = defaultWriteTimeout,
          readTimeout = defaultReadTimeout,
          ssl = ssl,
          pingInterval = defaultPingInterval,
          callTimeout = defaultCallTimeout
      )
  )

  /** @return The [HttpClientEndpointConfig] for the given client, populated with defaults as needed */
  operator fun get(clientName: String): HttpClientEndpointConfig =
      // TODO(mmihic): Cache, proxy, etc
      requireNotNull(endpoints[clientName]) { "no client configuration for endpoint $clientName" }
          .withDefaults(defaultClientConfig)
}

@Deprecated(
    "Use misk.endpoints.HttpClientSSLConfig",
    replaceWith = ReplaceWith("misk.endpoints.HttpClientSSLConfig")
)
typealias HttpClientSSLConfig = misk.endpoints.HttpClientSSLConfig

@Deprecated(
    "Use misk.endpoints.HttpClientEndpointConfig",
    replaceWith = ReplaceWith("misk.endpoints.HttpClientEndpointConfig")
)
typealias HttpClientEndpointConfig = HttpClientEndpointConfig