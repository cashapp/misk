package misk.client

import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

@Singleton
class HttpClientFactory {
  @Inject lateinit var sslLoader: SslLoader
  @Inject lateinit var sslContextFactory: SslContextFactory
  @com.google.inject.Inject(optional = true) lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val builder = unconfiguredClient.newBuilder()
    config.connectTimeout?.let { builder.connectTimeout(it.toMillis(),
        TimeUnit.MILLISECONDS) }
    config.readTimeout?.let { builder.readTimeout(it.toMillis(),
        TimeUnit.MILLISECONDS) }
    config.writeTimeout?.let { builder.writeTimeout(it.toMillis(),
        TimeUnit.MILLISECONDS) }
    config.ssl?.let {
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
      builder.dns(NoOpDns())
      // In-flight Envoy requests do not get properly closed when those requests aren't done via HTTP/2.
      // As such, ensure that communications via Envoy sidecar always happen over HTTP/2. Standard protocol
      // negotiation is fine for non-Envoy sidecar (read: non-unix socket) traffic.
      builder.protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
    }
    builder.proxy(Proxy.NO_PROXY)

    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}