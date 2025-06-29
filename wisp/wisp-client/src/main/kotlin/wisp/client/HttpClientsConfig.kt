package wisp.client

import wisp.security.ssl.CertStoreConfig
import wisp.security.ssl.TrustStoreConfig
import java.net.URL
import java.time.Duration

data class HttpClientsConfig @JvmOverloads constructor(
    // Need to retain ordering, hence LinkedHashMap
    val hostConfigs: LinkedHashMap<String, HttpClientConfig> = linkedMapOf(),
    val endpoints: Map<String, HttpClientEndpointConfig> = mapOf()
) {
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

data class HttpClientConfig @JvmOverloads constructor(
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
    val protocols: List<String>? = null,
    val retryOnConnectionFailure: Boolean? = null,
    val followRedirect: Boolean? = null,
    val followSslRedirects: Boolean? = null
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
        protocols = this.protocols ?: other.protocols,
      retryOnConnectionFailure = this.retryOnConnectionFailure ?: other.retryOnConnectionFailure,
      followRedirect = this.followRedirect ?: other.followRedirect,
      followSslRedirects = this.followSslRedirects ?: other.followSslRedirects
    )

data class HttpClientEndpointConfig @JvmOverloads constructor(
    val url: String? = null,
    val envoy: HttpClientEnvoyConfig? = null,
    val clientConfig: HttpClientConfig = HttpClientConfig()
) {
    init {
        require(url == null || envoy == null) { "Cannot set both url and envoy configs" }
    }
}

data class HttpClientEnvoyConfig @JvmOverloads constructor(
    val app: String,

    /** Environment to target. If null, the same environment as the app is running in is assumed. */
    val env: String? = null
)
