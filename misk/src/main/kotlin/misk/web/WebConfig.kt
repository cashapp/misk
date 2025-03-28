package misk.web

import misk.annotation.ExperimentalMiskApi
import misk.client.HTTP_SERVICE_UNAVAILABLE
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.TrustStoreConfig
import misk.web.concurrencylimits.ConcurrencyLimiterStrategy
import misk.web.exceptions.ActionExceptionLogLevelConfig
import org.slf4j.event.Level
import wisp.config.Config

data class WebConfig @JvmOverloads constructor(
  /** HTTP port to listen on, or 0 for any available port. */
  val port: Int,

  /**
   * If a connection is unused for this many milliseconds, it is closed. If zero, it is not closed.
   */
  val idle_timeout: Long = 0,

  /**
   * If >= 0,
   *  By default, use a dedicated jetty thread pool for health checking.
   *  With health_dedicated_jetty_instance = true, use a dedicated jetty instance.
   *
   * A dedicated thread pool ensures that health checks are not queued or rejected when the service
   * is saturated and queueing requests. If health checks are rejected and/or queued, the health
   * checks may fail and k8s will kill the container, even though it might be perfectly healthy. This
   * can cause cascading failures by sending more requests to other containers, resulting in longer
   * queues and more health checks failures.
   */
  val health_port: Int = -1,

  /**
   * @ExperimentalMiskApi - this feature is still being verified in production.
   *
   * health_port must be >= 0,
   *
   * A dedicated jetty instance ensures the service readiness and liveness endpoints
   * remain available during graceful shutdown to prevent the container from being forcefully
   * terminated and graceful failover from readiness check failures via HTTP 503.
   *
   * A separate jetty instance allows the primary instance to allow in flight connections to
   * complete before shutting down and then shut down supporting services orderly until all
   * are stopped and the health instance can shut down safely.
   */
  @property:ExperimentalMiskApi
  val health_dedicated_jetty_instance: Boolean = false,

  /**
   * @ExperimentalMiskApi - this feature is still being verified in production.
   *
   * health_port must be >= 0,
   * health_dedicated_jetty_instance must be true
   *
   * An interceptor which delays service shutdown for in flight requests to complete and for no
   * new incoming requests to have occurred during the idle time.  Once shutdown has commenced,
   * incoming requests will be redirected to healthy pods by responding with 503 or the status
   * code specified.
   *
   * This improves the ability for services to complete in-flight requests and safely terminate
   * the underlying connection.
   */
  @property:ExperimentalMiskApi
  val graceful_shutdown_config: GracefulShutdownConfig? = null,

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
  val close_connection_percent: Double = 0.0,

  /**
   * If true non-gRPC responses which are larger than the minGzipSize will be compressed.
   */
  val gzip: Boolean = true,

  /**
   * If true gRPC responses which are larger than the minGzipSize will be compressed.
   */
  val grpcGzip: Boolean = false,

  /** The minimum size in bytes before the response body will be compressed. */
  val minGzipSize: Int = 1024,

  val cors: Map<String, CorsConfig> = mapOf(),

  /** If true, disables automatic load shedding when degraded. */
  val concurrency_limiter_disabled: Boolean = true,

  /** The level of log when concurrency shedding. */
  val concurrency_limiter_log_level: Level = Level.ERROR,

  /* Custom configuration for calculating concurrency limits */
  val concurrency_limiter: ConcurrencyLimiterConfig? = ConcurrencyLimiterConfig(
    disabled = concurrency_limiter_disabled,
    strategy = ConcurrencyLimiterStrategy.GRADIENT2,
    max_concurrency = null,
    // 2 is chosen somewhat arbitrarily here. Most services have one or two endpoints that
    // receive the majority of traffic (power law, yay!), and those endpoints should _start up_
    // without triggering the concurrency limiter at the parallelism that we configured Jetty
    // to support.
    initial_limit = jetty_max_thread_pool_size / 2,
    log_level = concurrency_limiter_log_level,
  ),

  /** The number of milliseconds to sleep before commencing service shutdown. */
  val shutdown_sleep_ms: Int = 0,

  /** The maximum allowed size in bytes for the HTTP request line and HTTP request headers. */
  val http_request_header_size: Int? = 32768,

  /** The size of Jetty's header field cache, in terms of unique character branches. */
  val http_header_cache_size: Int? = null,

  /**
   * The number of milliseconds a connection can be idling before commencing service shutdown.
   * If zero, it is never closed and may cause ungraceful shutdown.
   *
   * Note: There is an underlying strategy to determine the default shutdown idle timeout.
   *  Use this value only when necessary.
   */
  val override_shutdown_idle_timeout: Long? = null,

  /**
   * How often readiness will re-run its status check.
   *
   * Ensure that [readiness_refresh_interval_ms] + "readiness latency" is less than [readiness_max_age_ms] or readiness will fail."
   */
  val readiness_refresh_interval_ms: Int = 1000,

  /** Maximum age of readiness status. If exceeded readiness will return an error */
  val readiness_max_age_ms: Int = 10000,

  /** If possible (e.g. running on JDK 21) misk will attempt to use a virtual thread executor for jetty. */
  val use_virtual_threads: Boolean = false,

  /** If true install NotFoundAction, the default action when a path is not found. */
  val install_default_not_found_action: Boolean = true,

  /** The output buffer size of Jetty (default is 32KB). */
  val jetty_output_buffer_size: Int? = null,

  /** The initial size of session's flow control receive window. */
  val jetty_initial_session_recv_window: Int? = null,

  /** The initial size of stream's flow control receive window. */
  val jetty_initial_stream_recv_window: Int? = null,

  /**
   * Whether to use direct ByteBuffers for reading.
   */
  val jetty_use_input_direct_byte_buffers: Boolean = true,

  /**
   * Whether to use direct ByteBuffers for writing.
   */
  val jetty_use_output_direct_byte_buffers: Boolean = true,

  /** Wires up health checks on whether Jetty's thread pool is low on threads. */
  val enable_thread_pool_health_check: Boolean = false,

  /** Configurations to enable Jetty to listen for traffic on a unix domain socket being proxied through a sidecar (e.g. envoy, istio) */
  val unix_domain_sockets: List<WebUnixDomainSocketConfig>? = null,
  ) : Config

data class WebSslConfig @JvmOverloads constructor(
  /** HTTPS port to listen on, or 0 for any available port. */
  val port: Int,
  val cert_store: CertStoreConfig,
  val trust_store: TrustStoreConfig? = null,
  val mutual_auth: MutualAuth = MutualAuth.REQUIRED,
  val cipher_compatibility: CipherCompatibility = CipherCompatibility.MODERN,
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

data class WebUnixDomainSocketConfig @JvmOverloads constructor(
  /** The Unix Domain Socket to listen on. Will attempt to use the JEP-380 connector when supported (using Java 16+ and file path) */
  val path: String,
  /** If true, the listener will support H2C. */
  val h2c: Boolean? = true
)

data class CorsConfig @JvmOverloads constructor(
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

data class ConcurrencyLimiterConfig @JvmOverloads constructor(
  /** If true, disables automatic load shedding when degraded. */
  val disabled: Boolean = false,

  /** The algorithm to use for determining concurrency limits. */
  val strategy: ConcurrencyLimiterStrategy = ConcurrencyLimiterStrategy.GRADIENT2,

  /** Minimum concurrency limit allowed. */
  val min_limit: Int? = null,

  /**
   * Maximum allowed concurrency limit providing an upper bound failsafe.
   */
  val max_concurrency: Int? = null,

  /** Initial limit used by the concurrency limiter. */
  val initial_limit: Int? = null,

  /**
   * The level of log when concurrency shedding. Same as concurrency_limiter_log_level default for
   * backwards compatibility.
   */
  val log_level: Level = Level.ERROR,
)

data class GracefulShutdownConfig @JvmOverloads constructor(
  /**
   * true to explicitly disable graceful shutdown service/interceptor.
   */
  val disabled: Boolean = false,

  /**
   * Milliseconds no new incoming requests have come in before proceeding with shutdown.
   */
  val idle_timeout: Long = 2000,

  /**
   * The maximum amount of time to wait for no in-flight requests and the idle timeout to expire.
   * If <= 0 the service will not limit the amount of time to wait for idle, however it might
   * be forcibly killed by an external source, like kubernetes.
   */
  val max_graceful_wait: Long = 20_000,

  /**
   * The status code to use for rejecting incoming requests after shutdown has begun.
   * If <= 0 the service will not reject incoming requests and wait until new requests have
   * processed and the idle_timeout is reached.
   */
  val rejection_status_code: Int = HTTP_SERVICE_UNAVAILABLE

)
