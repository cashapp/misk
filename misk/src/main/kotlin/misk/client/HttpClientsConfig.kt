package misk.client

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import misk.config.Config
import misk.logging.getLogger
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import java.net.URL
import java.time.Duration

@JsonDeserialize(converter = BackwardsCompatibleClientsConfigConverter::class)
data class HttpClientsConfig(
  @JsonAlias("hosts")
  //Need to retain ordering, hence LinkedHashMap
  val hostConfigs: LinkedHashMap<String, HttpClientConfig> = linkedMapOf(),
  val endpoints: Map<String, HttpClientEndpointConfig> = mapOf()
) : Config {
  init {
    validatePatterns()
  }

  /** @return The [HttpClientEndpointConfig] for the given client, populated with defaults as needed */
  operator fun get(clientName: String): HttpClientEndpointConfig {
    val endpointConfig = requireNotNull(endpoints[clientName]) {
        "no client configuration for endpoint $clientName"
    }

    val allMatchingConfigs = sequence {
      yield(httpClientConfigDefaults)
      yieldAll(
          endpointConfig.url?.let { url ->
            findUrlMatchingConfigs(url)
          } ?: findWildcardConfigs()
      )
      yield(endpointConfig.clientConfig)
    }.reduce { prev, cur -> cur.applyDefaults(prev) }

    return HttpClientEndpointConfig(
        url = endpointConfig.url,
        envoy = endpointConfig.envoy,
        clientConfig = allMatchingConfigs
    )
  }

  /** @return The [HttpClientEndpointConfig] for the given URL, populated with defaults as needed */
  operator fun get(url: URL): HttpClientEndpointConfig {
    val allMatchingConfigs = sequence {
      yield(httpClientConfigDefaults)
      yieldAll(
          findUrlMatchingConfigs(url.toString())
      )
    }.reduce { prev, cur -> cur.applyDefaults(prev) }

    return HttpClientEndpointConfig(
        url = url.toString(),
        clientConfig = allMatchingConfigs
    )
  }

  private fun validatePatterns() = try {
    endpoints.keys
        .map { it.toRegex(RegexOption.IGNORE_CASE) }
        .forEach { it.matches("") }
  } catch (e: Exception) {
    throw IllegalArgumentException(
        "Failed to initialize HttpClientsConfig, failed to parse Regexp patterns!",
        e
    )
  }

  private fun findWildcardConfigs() =
      hostConfigs.filter { (k, _) -> k == ".*" }.values

  private fun findUrlMatchingConfigs(url: String) =
      hostConfigs.filter { (k, _) ->
        k.toRegex(RegexOption.IGNORE_CASE).matches(URL(url).host)
      }.values

  companion object {
    val logger = getLogger<HttpClientsConfig>()
    val httpClientConfigDefaults = HttpClientConfig(
        maxRequests = 128,
        maxRequestsPerHost = 32,
        maxIdleConnections = 100,
        keepAliveDuration = Duration.ofMinutes(5)
    )
  }

  /** Names of configured endpoints, all of which can be fetched using [get] */
  fun endpointNames(): Set<String> = endpoints.keys
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
  val maxRequests: Int? = null,
  val maxRequestsPerHost: Int? = null,
  val maxIdleConnections: Int? = null,
  val keepAliveDuration: Duration? = null,
  val ssl: HttpClientSSLConfig? = null,
  val unixSocketFile: String? = null,
  val protocols: List<String>? = null
)

fun HttpClientConfig.applyDefaults(other: HttpClientConfig) =
    HttpClientConfig(
        connectTimeout = this.connectTimeout ?: other.connectTimeout,
        writeTimeout = this.writeTimeout ?: other.writeTimeout,
        readTimeout = this.readTimeout ?: other.readTimeout,
        pingInterval = this.pingInterval ?: other.pingInterval,
        callTimeout = this.callTimeout ?: other.callTimeout,
        maxRequests = this.maxRequests ?: other.maxRequests,
        maxRequestsPerHost = this.maxRequestsPerHost ?: other.maxRequestsPerHost,
        maxIdleConnections = this.maxIdleConnections ?: other.maxIdleConnections,
        keepAliveDuration = this.keepAliveDuration ?: other.keepAliveDuration,
        ssl = this.ssl ?: other.ssl,
        unixSocketFile = this.unixSocketFile ?: other.unixSocketFile,
        protocols = this.protocols ?: other.protocols
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
