package misk.web

import misk.config.Config
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig

data class WebConfig(
  val port: Int,
  val idle_timeout: Long,
  val host: String? = null,
  val ssl: WebSslConfig? = null,
  val selectors: Int? = null,
  val acceptors: Int? = null,
  val queue_size: Int? = null,
  val jetty_max_thread_pool_size: Int? = null,
  val action_exception_log_level: ActionExceptionLogLevelConfig = ActionExceptionLogLevelConfig(),
  // TODO(jayestrella): Add a sane default value to this.
  val close_connection_percent: Double = 0.0
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
