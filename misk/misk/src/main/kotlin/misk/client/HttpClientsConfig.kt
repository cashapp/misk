package misk.client

import misk.config.Config
import misk.security.ssl.KeystoreConfig
import misk.security.ssl.SslContextFactory
import okhttp3.OkHttpClient
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager

data class HttpClientsConfig(
  private val defaultConnectTimeout: Duration? = null,
  private val defaultWriteTimeout: Duration? = null,
  private val defaultReadTimeout: Duration? = null,
  private val ssl: HttpClientSSLConfig? = null,
  val endpoints: Map<String, HttpClientEndpointConfig> = mapOf()
) : Config {
  /** @return a client built from this configuration */
  fun newHttpClient(clientName: String): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val endpointConfig = endpoints[clientName] ?: throw IllegalArgumentException(
        "no client configuration for endpoint $clientName")

    val builder = unconfiguredClient.newBuilder()

    (endpointConfig.connectTimeout ?: defaultConnectTimeout)
        ?.let { builder.connectTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    (endpointConfig.writeTimeout ?: defaultWriteTimeout)
        ?.let { builder.writeTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    (endpointConfig.readTimeout ?: defaultReadTimeout)
        ?.let { builder.readTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    (endpointConfig.ssl ?: ssl)
        ?.let {
          val trustManagers = SslContextFactory.loadTrustManagers(it.truststore.load())
          val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
              ?: throw IllegalStateException(
                  "no x509 trust manager in ${it.truststore.path}")
          builder.sslSocketFactory(it.createSSLContext().socketFactory, x509TrustManager)
        }
    builder.proxy(Proxy.NO_PROXY)
    return builder.build()
  }

  private val unconfiguredClient = OkHttpClient()
}

data class HttpClientSSLConfig(
  val keystore: KeystoreConfig?,
  val truststore: KeystoreConfig
) {
  fun createSSLContext() = SslContextFactory.create(keystore, truststore)
}

data class HttpClientEndpointConfig(
  val url: String,
  val connectTimeout: Duration? = null,
  val writeTimeout: Duration? = null,
  val readTimeout: Duration? = null,
  val ssl: HttpClientSSLConfig? = null
)
