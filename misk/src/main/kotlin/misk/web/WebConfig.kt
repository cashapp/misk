package misk.web

import misk.config.Config
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import javax.servlet.FilterConfig

data class WebConfig(
  /** HTTP port to listen on, or 0 for any available port. */
  val port: Int,

  /** If a connection is unused for this many milliseconds, it is closed. */
  val idle_timeout: Long,

  /**
   * If >= 0, use a dedicated jetty thread pool for health checking.
   *
   * A dedicated thread pool ensures that health checks are not queued or rejected when the service
   * is saturated and queueing requests. If health checks are rejected and/or queued, the health
   * checks may fail and k8s will kill the container, even though it might be perfectly healthy. This
   * can cause cascading failures by sending more requests to other containers, resulting in longer
   * queues and more health checks failures.
   *
   * TODO(rhall): make this required
   */
  val health_port: Int = -1,

  /** The network interface to bind to. Null or 0.0.0.0 to bind to all interfaces. */
  val host: String? = null,

  val ssl: WebSslConfig? = null,

  /** Configuration to enable Jetty to listen for traffic on a unix domain socket being proxied through a sidecar
   * (like Envoy). */
  val unix_domain_socket: WebUnixDomainSocketConfig? = null,

  /** HTTP/2 support is currently opt-in because we can't load balance it dynamically. */
  val http2: Boolean = false,

  /** Number of NIO selector threads. */
  val selectors: Int? = null,

  /** Number of acceptor threads. */
  val acceptors: Int? = null,

  /** The accept backlog. */
  val queue_size: Int? = null,

  /** Maximum number of threads in Jetty's thread pool. */
  val jetty_max_thread_pool_size: Int = 200,

  /**
   * Maximum number of items in the queue for Jetty's thread pool.
   *
   * If 0, no queueing is used and requests are directly handed off to the thread pool. If a
   * thread is not available (i.e max threads in use) the request is rejected. Unfortunately Jetty
   * rejects requests by closing the socket instead of returning a 429. This can lead to confusing
   * EOFExceptions for the client.
   */
  val jetty_max_thread_pool_queue_size: Int = 300,

  /** Flag to enable thread pool queue metrics */
  // TODO make this true by default
  val enable_thread_pool_queue_metrics: Boolean = false,

  val action_exception_log_level: ActionExceptionLogLevelConfig = ActionExceptionLogLevelConfig(),

  /** The maximum number of streams per HTTP/2 connection. */
  val jetty_max_concurrent_streams: Int? = null,

  /** A value in [0.0..100.0]. Include 'Connection: close' in this percentage of responses. */
  val close_connection_percent: Double = 0.01,

  /**
   * If true responses which are larger than the minGzipSize will be compressed. Gzip compression
   * always enabled for requests and cannot be turned off.
   */
  val gzip: Boolean = true,

  /** The minimum size in bytes before the response body will be compressed. */
  val minGzipSize: Int = 1024,

  val cors: Map<String, CorsConfig> = mapOf()
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

data class WebUnixDomainSocketConfig(
  /** The Unix Domain Socket to listen on. */
  val path: String,
  /** If true, the listener will support H2C. */
  val h2c: Boolean? = true
)

data class CorsConfig(
    /** A comma separated list of origins that are allowed to access the resources. */
    val allowedOrigins: Array<String> = arrayOf("*"),
    /**
     * A comma separated list of HTTP methods that are allowed to be used when
     * accessing the resources.
     */
    val allowedMethods: Array<String> = arrayOf("GET", "POST", "HEAD"),
    /**
     * A comma separated list of HTTP headers that are allowed to be specified when
     * accessing the resources.
     */
    val allowedHeaders: Array<String> = arrayOf("X-Requested-With", "Content-Type", "Accept",
        "Origin"),
    /** A boolean indicating if the resource allows requests with credentials. */
    val allowCredentials: Boolean = true,
    /** The number of seconds that preflight requests can be cached by the client. */
    val preflightMaxAge: String = "1800",
    /**
     * True if preflight requests are chained to their target resource for normal handling
     * (as an OPTION request).
     */
    val chainPreflight: Boolean = true,
    /** A comma separated list of HTTP headers that are allowed to be exposed on the client. */
    val exposedHeaders: Array<String> = arrayOf()
)
