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
  private val endpoints: Map<String, HttpClientEndpointConfig> = mapOf()
) : Config {
  /** @return The [HttpClientEndpointConfig] for the given client, populated with defaults as needed */
  operator fun get(clientName: String): HttpClientEndpointConfig {
    // TODO(mmihic): Cache, proxy, etc
    val endpointConfig = endpoints[clientName] ?: throw IllegalArgumentException(
        "no client configuration for endpoint $clientName")

    return HttpClientEndpointConfig(
        endpointConfig.url,
        connectTimeout = endpointConfig.connectTimeout ?: defaultConnectTimeout,
        writeTimeout = endpointConfig.writeTimeout ?: defaultWriteTimeout,
        readTimeout = endpointConfig.readTimeout ?: defaultReadTimeout,
        ssl = endpointConfig.ssl ?: ssl
    )
  }
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
) {
  /** @return a client builder initialized based on this configuration */
  fun newHttpClient(): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val builder = unconfiguredClient.newBuilder()
    connectTimeout?.let { builder.connectTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    readTimeout?.let { builder.readTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    writeTimeout?.let { builder.writeTimeout(it.toMillis(), TimeUnit.MILLISECONDS) }
    ssl?.let {
      val trustManagers = SslContextFactory.loadTrustManagers(it.truststore.load())
      val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
          ?: throw IllegalStateException(
              "no x509 trust manager in ${it.truststore.path}")
      builder.sslSocketFactory(it.createSSLContext().socketFactory, x509TrustManager)
    }
    builder.proxy(Proxy.NO_PROXY)
    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
