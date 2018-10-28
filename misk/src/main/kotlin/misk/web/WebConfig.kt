package misk.web

import misk.config.Config
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig

data class WebConfig(
  val port: Int,
  val idle_timeout: Long,
  val host: String? = null,
  val ssl: WebSslConfig? = null,
  val selectors: Int? = null,
  val acceptors: Int? = null,
  val queueSize: Int? = null,
  val default_protocol: HttpProtocol = HttpProtocol.HTTP2
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
}

enum class HttpProtocol {
  HTTP2,
  HTTP1_1
}