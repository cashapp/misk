package misk.client

import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

@Singleton
class HttpClientFactory @Inject constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory
) {
  @com.google.inject.Inject(optional = true)
  lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val builder = unconfiguredClient.newBuilder()
    config.clientConfig.connectTimeout?.let { builder.connectTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    config.clientConfig.readTimeout?.let { builder.readTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    config.clientConfig.writeTimeout?.let { builder.writeTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    config.clientConfig.pingInterval?.let { builder.pingInterval(it) }
    config.clientConfig.callTimeout?.let { builder.callTimeout(it) }
    config.clientConfig.ssl?.let {
      val trustStore = sslLoader.loadTrustStore(it.trust_store)!!
      val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
      val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
          ?: throw IllegalStateException("no x509 trust manager in ${it.trust_store}")
      val sslContext = sslContextFactory.create(it.cert_store, it.trust_store)
      builder.sslSocketFactory(sslContext.socketFactory, x509TrustManager)
    }
    config.envoy?.let {
      builder.socketFactory(
          UnixDomainSocketFactory(envoyClientEndpointProvider.unixSocket(config.envoy)))
      // No DNS lookup needed since we're just sending the request over a socket.
      builder.dns(NoOpDns)
      // Envoy is the proxy
      builder.proxy(Proxy.NO_PROXY)
      // OkHttp <=> envoy over h2 has bad interactions, and benefit is marginal
      builder.protocols(listOf(Protocol.HTTP_1_1))
    }

    val dispatcher = Dispatcher()
    dispatcher.maxRequests = config.clientConfig.maxRequests
    dispatcher.maxRequestsPerHost = config.clientConfig.maxRequestsPerHost
    builder.dispatcher(dispatcher)

    val connectionPool = ConnectionPool(
        config.clientConfig.maxIdleConnections,
        config.clientConfig.keepAliveDuration.toMillis(),
        TimeUnit.MILLISECONDS)
    builder.connectionPool(connectionPool)

    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
