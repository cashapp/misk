package misk.web

import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader
import wisp.deployment.TESTING

/**
 * A module that starts an embedded Jetty web server configured for testing. The server supports
 * both plaintext and TLS.
 */
@Deprecated(
  message = "It's replace by WebServerTestingModule + MiskTestingServiceModule to facilitate" +
    "the composability of testing modules for application owners",
  replaceWith = ReplaceWith(expression = "WebServerTestingModule", "misk.web")
)
class WebTestingModule(
  private val webConfig: WebConfig = TESTING_WEB_CONFIG
) : KAbstractModule() {
  override fun configure() {
    install(WebServerTestingModule(webConfig))
    install(MiskTestingServiceModule())
  }

  companion object {
    val TESTING_WEB_CONFIG = WebServerTestingModule.TESTING_WEB_CONFIG
  }
}

/**
 * A module that starts an embedded Jetty web server configured for testing. The server supports
 * both plaintext and TLS.
 */
class WebServerTestingModule(
  private val webConfig: WebConfig = TESTING_WEB_CONFIG
) : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(MiskWebModule(webConfig))
  }

  companion object {
    val TESTING_WEB_CONFIG = WebConfig(
      // 0 results in a random port
      port = 0,
      health_port = 0,
      // use a deterministic number for selector/acceptor threads since the dynamic number can
      // vary local vs CI. this allows writing thread exhaustion tests.
      acceptors = 1,
      selectors = 1,
      idle_timeout = 500000,
      host = "127.0.0.1",
      ssl = WebSslConfig(
        port = 0,
        cert_store = CertStoreConfig(
          resource = "classpath:/ssl/server_cert_key_combo.pem",
          passphrase = "serverpassword",
          format = SslLoader.FORMAT_PEM
        ),
        mutual_auth = WebSslConfig.MutualAuth.NONE
      )
    )
  }
}
