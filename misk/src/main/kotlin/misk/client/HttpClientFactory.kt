package misk.client

import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager


private object Defaults {
  /*
    Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
   */
  val maxIdleConnections = 5

  /*
    Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
   */
  val keepAliveDuration = Duration.ofMinutes(5)
}

@Singleton
class HttpClientFactory @Inject constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory
) {
  @com.google.inject.Inject(optional = true)
  lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  @com.google.inject.Inject(optional = true)
  var okhttpInterceptors: Provider<List<Interceptor>>? = null

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

    require(config.envoy == null || config.clientConfig.unixSocketFile == null) {
      "Setting both `envoy` and `unixSocketFile` on `HttpClientEndpointConfig` is not supported!"
    }

    require(config.envoy == null || config.clientConfig.protocols == null) {
      "Setting both `envoy` and `protocols` on `HttpClientEndpointConfig` is not supported!"
    }

    config.clientConfig.unixSocketFile?.let {
      builder.socketFactory(UnixDomainSocketFactory(File(it)))
      // No DNS lookup needed since we're just sending the request over a socket.
      builder.dns(NoOpDns)
      // Proxy config not supported
      builder.proxy(Proxy.NO_PROXY)
    }

    config.clientConfig.protocols?.let {
      builder.protocols(
          config.clientConfig.protocols.map { Protocol.get(it) }
      )
    }

    config.envoy?.let {
      builder.socketFactory(
          UnixDomainSocketFactory(envoyClientEndpointProvider.unixSocket(config.envoy)))
      // No DNS lookup needed since we're just sending the request over a socket.
      builder.dns(NoOpDns)
      // Proxy config not supported
      builder.proxy(Proxy.NO_PROXY)
      // OkHttp <=> envoy over h2 has bad interactions, and benefit is marginal
      builder.protocols(listOf(Protocol.HTTP_1_1))
    }

    val dispatcher = Dispatcher()
    config.clientConfig.maxRequests?.let { maxRequests ->
      dispatcher.maxRequests = maxRequests
    }
    config.clientConfig.maxRequestsPerHost?.let { maxRequestsPerHost ->
      dispatcher.maxRequestsPerHost = maxRequestsPerHost
    }
    builder.dispatcher(dispatcher)

    val connectionPool = ConnectionPool(
        config.clientConfig.maxIdleConnections ?: Defaults.maxIdleConnections,
        (config.clientConfig.keepAliveDuration?:Defaults.keepAliveDuration).toMillis(),
        TimeUnit.MILLISECONDS)
    builder.connectionPool(connectionPool)

    okhttpInterceptors?.let {
      builder.interceptors().addAll(it.get())
    }

    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
