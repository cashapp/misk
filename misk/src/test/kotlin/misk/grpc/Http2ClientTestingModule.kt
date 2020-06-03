package misk.grpc

import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.endpoints.HttpClientConfig
import misk.endpoints.HttpClientSSLConfig
import misk.endpoints.buildClientEndpointConfig
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.web.jetty.JettyService
import java.net.URL
import javax.inject.Singleton

/**
 * Configures an OkHttp client with HTTPS and HTTP/2.
 *
 * Create a client-specific injector for this. This is necessary because the server doesn't get a
 * port until after it starts.
 */
class Http2ClientTestingModule(val jetty: JettyService) : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(HttpClientModule("default"))
  }

  @Provides
  @Singleton
  fun provideHttpClientsConfig() = HttpClientsConfig(
      "default" to URL("http://example.com/").buildClientEndpointConfig(httpClientConfig)
  )

  private val httpClientConfig = HttpClientConfig(
      ssl = HttpClientSSLConfig(
          cert_store = null,
          trust_store = TrustStoreConfig(
              resource = "classpath:/ssl/server_cert.pem",
              format = SslLoader.FORMAT_PEM
          )
      )
  )
}
