package misk.web

import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import org.slf4j.event.Level
import wisp.config.Config

data class WebConfig(
  /** HTTP port to listen on, or 0 for any available port. */
  val port: Int,

  /**
   * If a connection is unused for this many milliseconds, it is closed. If zero, it is not closed.
   */
  val idle_timeout: Long = 0,

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

  /** Minimum number of threads in Jetty's thread pool. */
  val jetty_min_thread_pool_size: Int = 8,

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
   * If true responses which are larger than the minGzipSize will be compressed.
   */
  val gzip: Boolean = true,

  /** The minimum size in bytes before the response body will be compressed. */
  val minGzipSize: Int = 1024,

  val cors: Map<String, CorsConfig> = mapOf(),

  /** If true, disables automatic load shedding when degraded. */
  val concurrency_limiter_disabled: Boolean = false,

  /** The level of log when concurrency shedding. */
  val concurrency_limiter_log_level: Level = Level.ERROR,

  /** The number of milliseconds to sleep before commencing service shutdown. */
  val shutdown_sleep_ms: Int = 0,
) : Config {
  @Deprecated(
    message = "obsolete; for binary-compatibility only",
    level = DeprecationLevel.HIDDEN,
  )
  constructor(
    port: Int,
    idle_timeout: Long = 0,
    health_port: Int = -1,
    host: String? = null,
    ssl: WebSslConfig? = null,
    unix_domain_socket: WebUnixDomainSocketConfig? = null,
    http2: Boolean = false,
    selectors: Int? = null,
    acceptors: Int? = null,
    queue_size: Int? = null,
    jetty_max_thread_pool_size: Int = 200,
    jetty_min_thread_pool_size: Int = 8,
    jetty_max_thread_pool_queue_size: Int = 300,
    enable_thread_pool_queue_metrics: Boolean = false,
    action_exception_log_level: ActionExceptionLogLevelConfig = ActionExceptionLogLevelConfig(),
    jetty_max_concurrent_streams: Int? = null,
    close_connection_percent: Double = 0.01,
    gzip: Boolean = true,
    minGzipSize: Int = 1024,
    cors: Map<String, CorsConfig> = mapOf(),
    concurrency_limiter_disabled: Boolean = false,
    shutdown_sleep_ms: Int = 0,
  ) : this(
    port = port,
    idle_timeout = idle_timeout,
    health_port = health_port,
    host = host,
    ssl = ssl,
    unix_domain_socket = unix_domain_socket,
    http2 = http2,
    selectors = selectors,
    acceptors = acceptors,
    queue_size = queue_size,
    jetty_max_thread_pool_size = jetty_max_thread_pool_size,
    jetty_min_thread_pool_size = jetty_min_thread_pool_size,
    jetty_max_thread_pool_queue_size = jetty_max_thread_pool_queue_size,
    enable_thread_pool_queue_metrics = enable_thread_pool_queue_metrics,
    action_exception_log_level = action_exception_log_level,
    jetty_max_concurrent_streams = jetty_max_concurrent_streams,
    close_connection_percent = close_connection_percent,
    gzip = gzip,
    minGzipSize = minGzipSize,
    cors = cors,
    concurrency_limiter_disabled = concurrency_limiter_disabled,
    concurrency_limiter_log_level = Level.ERROR,
    shutdown_sleep_ms = shutdown_sleep_ms,
  )
}

data class WebSslConfig(
  /** HTTPS port to listen on, or 0 for any available port. */
  val port: Int,
  val cert_store: CertStoreConfig,
  val trust_store: TrustStoreConfig? = null,
  val mutual_auth: MutualAuth = MutualAuth.REQUIRED,
  val cipher_compatibility: CipherCompatibility = CipherCompatibility.COMPATIBLE,
) {
  enum class MutualAuth {
    NONE,
    REQUIRED,
    DESIRED
  }

  // These enum variants here use the terminology defined at
  // https://cloud.google.com/load-balancing/docs/ssl-policies-concepts
  enum class CipherCompatibility {
    /** Allows the broadest set of clients, including clients that support only out-of-date SSL features. */
    COMPATIBLE,

    /** Supports a wide set of SSL features, allowing modern clients to negotiate SSL. */
    MODERN,

    /** Supports a reduced set of SSL features, intended to meet stricter compliance requirements. */
    RESTRICTED
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
  val allowedHeaders: Array<String> = arrayOf(
    "X-Requested-With", "Content-Type", "Accept",
    "Origin"
  ),
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
