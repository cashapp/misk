package wisp.client

import wisp.security.ssl.SslContextFactory
import wisp.security.ssl.SslLoader
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.LoggingEventListener
import java.io.File
import java.net.Proxy
import javax.net.ssl.X509TrustManager

class HttpClientFactory constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory,
  private val okHttpClientCommonConfigurator: OkHttpClientCommonConfigurator,
  private val envoyClientEndpointProvider: EnvoyClientEndpointProvider?,
  private val okhttpInterceptors: List<Interceptor>?
) {
  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val builder = unconfiguredClient.newBuilder()
    builder.retryOnConnectionFailure(false)
    okHttpClientCommonConfigurator.configure(builder = builder, config = config)
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

    config.clientConfig.protocols
      ?.map { Protocol.get(it) }
      ?.let { builder.protocols(it) }

    config.envoy?.let {
      builder.socketFactory(
        UnixDomainSocketFactory(envoyClientEndpointProvider!!.unixSocket(config.envoy))
      )
      // No DNS lookup needed since we're just sending the request over a socket.
      builder.dns(NoOpDns)
      // Proxy config not supported
      builder.proxy(Proxy.NO_PROXY)
      // OkHttp <=> envoy over h2 has bad interactions, and benefit is marginal
      builder.protocols(listOf(Protocol.HTTP_1_1))
    }

    okhttpInterceptors?.let {
      builder.interceptors().addAll(it)
    }

    builder.eventListenerFactory(LoggingEventListener.Factory())

    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
