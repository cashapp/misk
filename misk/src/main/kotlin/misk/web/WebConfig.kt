package misk.web

import misk.config.Config
import misk.security.ssl.KeystoreConfig
import misk.security.ssl.SslContextFactory

data class WebConfig(
    val port: Int,
    val idle_timeout: Long,
    val host: String? = null,
    val ssl: WebSslConfig? = null
) : Config

data class WebSslConfig(
    val port: Int,
    val keystore: KeystoreConfig,
    val truststore: KeystoreConfig? = null,
    val mutual_auth: MutualAuth = MutualAuth.REQUIRED
) {
  enum class MutualAuth {
    NONE,
    REQUIRED,
    DESIRED
  }
  fun createSSLContext() = SslContextFactory.create(keystore, truststore)
}
