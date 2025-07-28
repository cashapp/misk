package misk.client

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File
import java.net.Proxy
import java.net.ProxySelector
import javax.net.ssl.X509TrustManager
import misk.resources.ResourceLoader
import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import wisp.client.EnvoyClientEndpointProvider
import wisp.client.UnixDomainSocketFactory

@Singleton
class HttpClientFactory
@Inject
constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory,
  private val okHttpClientCommonConfigurator: OkHttpClientCommonConfigurator,
) {

  constructor(
    sslLoader: SslLoader = SslLoader(ResourceLoader.SYSTEM),
    sslContextFactory: SslContextFactory = SslContextFactory(sslLoader),
    okHttpClientCommonConfigurator: OkHttpClientCommonConfigurator = OkHttpClientCommonConfigurator(),
    envoyClientEndpointProvider: EnvoyClientEndpointProvider? = null,
    okhttpInterceptors: List<Interceptor>? = null,
    proxySelector: ProxySelector? = null,
  ) : this(sslLoader, sslContextFactory, okHttpClientCommonConfigurator) {
    this.envoyClientEndpointProvider = envoyClientEndpointProvider
    this.okhttpInterceptors = Provider { okhttpInterceptors }
    this.proxySelector = proxySelector
  }

  // Field-injected so ClientLoggingInterceptor remains internal.
  @Inject private lateinit var clientLoggingInterceptor: ClientLoggingInterceptor

  @Inject private lateinit var clientMetricsInterceptorFactory: ClientMetricsInterceptor.Factory

  @com.google.inject.Inject(optional = true) var envoyClientEndpointProvider: EnvoyClientEndpointProvider? = null

  @com.google.inject.Inject(optional = true) var okhttpInterceptors: Provider<List<Interceptor>>? = null

  @com.google.inject.Inject(optional = true) var proxySelector: ProxySelector? = null

  /** Returns a client initialized based on `config`. */
  @JvmOverloads
  fun create(config: HttpClientEndpointConfig, serviceName: String? = null): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val builder = unconfiguredClient.newBuilder()
    builder.interceptors().add(clientLoggingInterceptor)
    if (serviceName != null) {
      builder.interceptors().add(clientMetricsInterceptorFactory.create(serviceName))
    }
    okHttpClientCommonConfigurator.configure(builder = builder, config = config)
    config.clientConfig.ssl?.let {
      val trustStore = sslLoader.loadTrustStore(it.trust_store)!!
      val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
      val x509TrustManager =
        trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
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
      builder.dns(wisp.client.NoOpDns)
      // Proxy config not supported
      builder.proxy(Proxy.NO_PROXY)
    }

    config.clientConfig.protocols?.map { Protocol.get(it) }?.let { builder.protocols(it) }

    config.envoy?.let {
      builder.socketFactory(
        UnixDomainSocketFactory(envoyClientEndpointProvider!!.unixSocket(config.envoy.toWispConfig()))
      )
      // No DNS lookup needed since we're just sending the request over a socket.
      builder.dns(NoOpDns)
      // Proxy config not supported
      builder.proxy(Proxy.NO_PROXY)
      // OkHttp <=> envoy over h2 has bad interactions, and benefit is marginal
      builder.protocols(listOf(Protocol.HTTP_1_1))
    }

    okhttpInterceptors?.let { builder.interceptors().addAll(it.get()) }

    proxySelector?.let { builder.proxySelector(it) }

    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
