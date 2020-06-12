package misk.web

import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader

/**
 * A module that starts an embedded Jetty web server configured for testing. The server supports
 * both plaintext and TLS.
 */
class WebTestingModule(
  private val webConfig: WebConfig = TESTING_WEB_CONFIG
) : KAbstractModule() {
  override fun configure() {
    install(EnvironmentModule(Environment.TESTING))
    install(MiskTestingServiceModule())
    install(MiskWebModule(webConfig))
  }

  companion object {
    val TESTING_WEB_CONFIG = WebConfig(
        // 0 results in a random port
        port = 0,
        health_port = 0,
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
