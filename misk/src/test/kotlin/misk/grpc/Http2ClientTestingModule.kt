package misk.grpc

import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.web.jetty.JettyService
import java.time.Duration
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
  fun provideHttpClientsConfig(): HttpClientsConfig {
    return HttpClientsConfig(
      endpoints = mapOf(
        "default" to HttpClientEndpointConfig(
          url = "http://example.com/",
          clientConfig = HttpClientConfig(
            ssl = HttpClientSSLConfig(
              cert_store = null,
              trust_store = TrustStoreConfig(
                resource = "classpath:/ssl/server_cert.pem",
                format = SslLoader.FORMAT_PEM
              )
            ),
            callTimeout = Duration.ofMillis(1000)
          )
        )
      )
    )
  }
}
