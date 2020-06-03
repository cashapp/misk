package misk.client

import misk.endpoints.HttpClientConfig
import misk.endpoints.HttpClientEndpointConfig
import misk.endpoints.HttpEndpoint
import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

private object ClientConfigDefaults {
  val maxRequests = 128
  val maxRequestsPerHost = 32
  val maxIdleConnections = 100
  val keepAliveDuration = Duration.ofMinutes(5)
}

@Singleton
class HttpClientFactory @Inject constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory
) {
  @com.google.inject.Inject(optional = true)
  lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient =
      // TODO(mmihic): Cache, proxy, etc
      OkHttpClient.Builder().apply {
        clientConfig(config.httpClientConfig)
        when (val endpoint = config.endpoint) {
          is HttpEndpoint.Envoy -> envoyConfig(endpoint)
        }
      }.build()

  private fun OkHttpClient.Builder.clientConfig(
    httpClientConfig: HttpClientConfig
  ) = apply {
    httpClientConfig.connectTimeout?.let(::connectTimeout)
    httpClientConfig.readTimeout?.let(::readTimeout)
    httpClientConfig.writeTimeout?.let(::writeTimeout)
    httpClientConfig.pingInterval?.let(::pingInterval)
    httpClientConfig.callTimeout?.let(::callTimeout)

    httpClientConfig.ssl?.let {
      val trustStore = sslLoader.loadTrustStore(it.trust_store)!!
      val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
      val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
          ?: throw IllegalStateException("no x509 trust manager in ${it.trust_store}")
      val sslContext = sslContextFactory.create(it.cert_store, it.trust_store)

      sslSocketFactory(sslContext.socketFactory, x509TrustManager)
    }

    dispatcher(Dispatcher(
        maxRequests = httpClientConfig.maxRequests
            ?: ClientConfigDefaults.maxRequests,
        maxRequestsPerHost = httpClientConfig.maxRequestsPerHost
            ?: ClientConfigDefaults.maxRequestsPerHost
    ))

    connectionPool(ConnectionPool(
        maxIdleConnections = httpClientConfig.maxIdleConnections
            ?: ClientConfigDefaults.maxIdleConnections,
        keepAliveDuration = httpClientConfig.keepAliveDuration
            ?: ClientConfigDefaults.keepAliveDuration
    ))
  }

  private fun OkHttpClient.Builder.envoyConfig(
    envoyConfig: HttpEndpoint.Envoy
  ) = apply {
    val socket = envoyClientEndpointProvider.unixSocket(envoyConfig)
    socketFactory(UnixDomainSocketFactory(socket))
    // No DNS lookup needed since we're just sending the request over a socket.
    dns(NoOpDns)
    // Envoy is the proxy
    proxy(Proxy.NO_PROXY)
    // OkHttp <=> envoy over h2 has bad interactions, and benefit is marginal
    protocols(listOf(Protocol.HTTP_1_1))
  }
}

private fun Dispatcher(
  maxRequests: Int,
  maxRequestsPerHost: Int
) = Dispatcher().apply {
  this.maxRequests = maxRequests
  this.maxRequestsPerHost = maxRequestsPerHost
}

//Helper factory function
private fun ConnectionPool(
  maxIdleConnections: Int,
  keepAliveDuration: Duration
) = ConnectionPool(
    maxIdleConnections = maxIdleConnections,
    keepAliveDuration = keepAliveDuration.toMillis(),
    timeUnit = TimeUnit.MILLISECONDS
)