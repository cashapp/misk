package misk.web

import misk.config.Config
import misk.security.ssl.CertStore
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStore
import misk.security.ssl.TrustStoreConfig

data class WebConfig(
  val port: Int,
  val idle_timeout: Long,
  val host: String? = null,
  val ssl: WebSslConfig? = null
) : Config

data class WebSslConfig(
  val port: Int,
  val cert_store: CertStoreConfig,
  val trust_store: TrustStoreConfig? = null,
  val mutual_auth: MutualAuth = MutualAuth.REQUIRED
) {
  enum class MutualAuth {
    NONE,
    REQUIRED,
    DESIRED
  }

  fun buildCertStore(): CertStore = cert_store.load()!!
  fun buildTrustStore(): TrustStore? = trust_store?.load()
}
