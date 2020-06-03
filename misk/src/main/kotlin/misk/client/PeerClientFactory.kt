package misk.client

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.inject.Provides
import misk.clustering.Cluster
import misk.config.AppName
import misk.endpoints.HttpEndpoint
import misk.inject.KAbstractModule
import misk.security.cert.X500Name
import misk.web.WebConfig
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier

/**
 * Binds a [PeerClientFactory] that calls peers on the HTTPS port of this process's server,
 * as determined by the SSL port in the [WebConfig].
 */
class PeerClientModule : KAbstractModule() {
  @Provides @Singleton fun peerClientFactory(
    @AppName appName: String,
    httpClientsConfig: HttpClientsConfig,
    httpClientFactory: HttpClientFactory,
    webConfig: WebConfig
  ): PeerClientFactory {
    check(webConfig.ssl?.port ?: 0 > 0) { "server must have static HTTPS port" }

    return PeerClientFactory(
        appName = appName,
        httpClientsConfig = httpClientsConfig,
        httpClientFactory = httpClientFactory,
        httpsPort = webConfig.ssl!!.port
    )
  }
}

/**
 * For testing.
 *
 * Binds a [PeerClientFactory] that calls peers on the HTTPS port of this process's server,
 * as determined by the Jetty server's port.
 */
class JettyPortPeerClientModule : KAbstractModule() {
  @Provides @Singleton fun peerClientFactory(
    @AppName appName: String,
    httpClientsConfig: HttpClientsConfig,
    httpClientFactory: HttpClientFactory,
    jetty: JettyService
  ): PeerClientFactory {
    return PeerClientFactory(
        appName = appName,
        httpClientsConfig = httpClientsConfig,
        httpClientFactory = httpClientFactory,
        httpsPort = jetty.httpsServerUrl!!.port
    )
  }
}

/**
 * Factory that creates [OkHttpClient]s for connecting to another instance of the same application
 * running in the same cluster.
 *
 * An [OkHttpClient] is cached for each peer.
 */
class PeerClientFactory(
  private val appName: String,
  private val httpClientsConfig: HttpClientsConfig,
  private val httpClientFactory: HttpClientFactory,
  private val httpsPort: Int
) {
  private val cache = CacheBuilder.newBuilder()
      .expireAfterAccess(5, TimeUnit.MINUTES)
      .build(object : CacheLoader<Cluster.Member, OkHttpClient>() {
        override fun load(peer: Cluster.Member): OkHttpClient {
          val config = httpClientsConfig[appName].copy(
              endpoint = HttpEndpoint.Url(baseUrl(peer))
          )

          return httpClientFactory.create(config).newBuilder()
              .hostnameVerifier(HostnameVerifier { _, session ->
                val ou =
                    (session?.peerCertificates?.firstOrNull() as? X509Certificate)?.let { peerCert ->
                      X500Name.parse(peerCert.subjectX500Principal.name).organizationalUnit
                    }
                appName == ou
              })
              .build()
        }
      })

  init {
    require(httpsPort > 0) { "port must be a positive integer " }

    // There must be web client config. The URL and Envoy config are ultimately ignored.
    httpClientsConfig[appName] // This throws if config is missing
  }

  /** Get the base URL for calling the given peer cluster member. */
  fun baseUrl(peer: Cluster.Member): String {
    return "https://${peer.ipAddress}:$httpsPort"
  }

  /**
   * Get a client to call the given peer cluster member.
   * This client will fail when calling different services, as determined by the OU in the certificate
   * returned by the called service.
   */
  fun client(peer: Cluster.Member): OkHttpClient = cache[peer]
}
