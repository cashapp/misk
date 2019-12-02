package misk.web

import misk.config.Config
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig

data class WebConfig(
  /** HTTP port to listen on, or 0 for any available port. */
  val port: Int,

  /** If a connection is unused for this many milliseconds, it is closed. */
  val idle_timeout: Long,

  /** The network interface to bind to. Null or 0.0.0.0 to bind to all interfaces. */
  val host: String? = null,

  val ssl: WebSslConfig? = null,

  /** HTTP/2 support is currently opt-in because we can't load balance it dynamically. */
  val http2: Boolean = false,

  /** Number of NIO selector threads. */
  val selectors: Int? = null,

  /** Number of acceptor threads. */
  val acceptors: Int? = null,

  /** The accept backlog. */
  val queue_size: Int? = null,

  /** Maximum number of threads in Jetty's thread pool. */
  val jetty_max_thread_pool_size: Int? = null,

  val action_exception_log_level: ActionExceptionLogLevelConfig = ActionExceptionLogLevelConfig(),

  /** The maximum number of streams per HTTP/2 connection. */
  val jetty_max_concurrent_streams: Int? = null,

  /** A value in [0.0..100.0]. Include 'Connection: close' in this percentage of responses. */
  // TODO(jayestrella): Add a sane default value to this.
  val close_connection_percent: Double = 0.0,

  /** If true responses which are larger than the minGzipSize will be compressed. */
  val gzip: Boolean = false,
  /** The minimum size in bytes before the response will be compressed. */
  val minGzipSize: Int = 1024
) : Config

data class WebSslConfig(
  /** HTTPS port to listen on, or 0 for any available port. */
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
