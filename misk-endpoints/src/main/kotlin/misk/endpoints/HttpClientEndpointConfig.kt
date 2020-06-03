package misk.endpoints

import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import okhttp3.HttpUrl
import java.net.URL
import java.time.Duration

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
  val ssl: HttpClientSSLConfig? = null
) {
  fun withDefaults(defaults: HttpClientConfig) = HttpClientConfig(
      connectTimeout = this.connectTimeout ?: defaults.connectTimeout,
      writeTimeout = this.writeTimeout ?: defaults.writeTimeout,
      readTimeout = this.readTimeout ?: defaults.readTimeout,
      pingInterval = this.pingInterval ?: defaults.pingInterval,
      callTimeout = this.callTimeout ?: defaults.callTimeout,
      maxRequests = this.maxRequests ?: defaults.maxRequests,
      maxRequestsPerHost = this.maxRequestsPerHost ?: defaults.maxRequestsPerHost,
      maxIdleConnections = this.maxIdleConnections ?: defaults.maxIdleConnections,
      keepAliveDuration = this.keepAliveDuration ?: defaults.keepAliveDuration,
      ssl = this.ssl ?: defaults.ssl
  )
}

sealed class HttpEndpoint {
  data class Url(
    val url: String
  ) : HttpEndpoint() {
    constructor(httpUrl: HttpUrl) : this(httpUrl.toString())
    constructor(url: URL) : this(url.toString())
  }

  data class Envoy(
    val app: String,

    /** Environment to target. If null, the same environment as the app is running in is assumed. */
    val env: String? = null
  ) : HttpEndpoint()

  fun <T> map(whenUrl: (Url) -> T, whenEnvoy: (Envoy) -> T): T =
      when (this) {
        is Url -> whenUrl(this)
        is Envoy -> whenEnvoy(this)
      }
}

fun HttpUrl.buildClientEndpointConfig(
  clientConfig: HttpClientConfig = HttpClientConfig()
) = HttpClientEndpointConfig(HttpEndpoint.Url(this), clientConfig)

fun URL.buildClientEndpointConfig(
  clientConfig: HttpClientConfig = HttpClientConfig()
) = HttpClientEndpointConfig(HttpEndpoint.Url(this), clientConfig)

fun HttpEndpoint.buildClientEndpointConfig(
  clientConfig: HttpClientConfig = HttpClientConfig()
) = HttpClientEndpointConfig(this, clientConfig)

data class HttpClientEndpointConfig(
  val endpoint: HttpEndpoint,
  val httpClientConfig: HttpClientConfig = HttpClientConfig()
) {
  @Deprecated("Backwards compatible constructor")
  constructor(
    url: String? = null,
    envoy: HttpEndpoint.Envoy? = null,
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
  ) : this(
      when {
        url != null && envoy != null ->
          error("Only one of `url` or `envoy` parameters should be specified")
        url != null -> HttpEndpoint.Url(url)
        envoy != null -> envoy
        else -> error("One of `url` or `envoy` parameters is expected to be specified")
      },
      HttpClientConfig(
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

  @Deprecated(
      "Backwards compatible getter. Please use `endpoint` directly",
      replaceWith = ReplaceWith("endpoint.url")
  )
  val url: String?
    get() =
      when (endpoint) {
        is HttpEndpoint.Url -> endpoint.url
        else -> null
      }

  @Deprecated(
      "Backwards compatible getter. Please use `endpoint` directly",
      replaceWith = ReplaceWith("endpoint")
  )
  val envoy: HttpEndpoint.Envoy?
    get() =
      when (endpoint) {
        is HttpEndpoint.Envoy -> endpoint
        else -> null
      }

  fun withDefaults(defaults: HttpClientConfig) = HttpClientEndpointConfig(
      endpoint = endpoint,
      httpClientConfig = httpClientConfig.withDefaults(defaults)
  )
}

@Deprecated(
    "Backwards compatible alias. Please use `HttpEndpoint.Envoy`",
    replaceWith = ReplaceWith(
        "HttpEndpoint.Envoy",
        "misk.endpoints.HttpEndpoint"
    )
)
typealias HttpClientEnvoyConfig = HttpEndpoint.Envoy