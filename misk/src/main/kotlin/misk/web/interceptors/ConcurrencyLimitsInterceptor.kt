package misk.web.interceptors

import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.netflix.concurrency.limits.Limiter
import com.netflix.concurrency.limits.limit.VegasLimit
import com.netflix.concurrency.limits.limiter.AbstractLimiter
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import misk.Action
import misk.metrics.Metrics
import misk.web.AvailableWhenDegraded
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.WebConfig
import org.slf4j.event.Level
import wisp.logging.getLogger
import wisp.logging.log
import java.net.HttpURLConnection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

/**
 * Detects degraded behavior and sheds requests accordingly. Internally this uses adaptive limiting
 * as implemented by Netflix's [concurrency-limits][concurrency_limits] library. It implements some
 * of the recommendations in [Using Load Shedding to Avoid Overload][avoid_overload].
 *
 * This annotation is applied to all actions by default. Opt-out with [AvailableWhenDegraded].
 *
 * Throughput predictions are made independently for each action. To bucket calls more finely, or to
 * bucket calls that span actions, use the Quota-Path HTTP header. Callers may optionally include
 * this HTTP header with a path-like string:
 *
 * ```
 * POST /squareup.event_consumer.service.HandleService/Handle HTTP/2
 * Content-Type: application/grpc
 * Quota-Path: /consumer/money_movement_events
 * ...
 * ```
 *
 * If a Quota-Path header is included, it replaces the action as the scope for concurrency limiting.
 * If the same Quota-Path header is used on different actions, the concurrency limits of these
 * actions are shared.
 *
 * [concurrency_limits]: https://github.com/Netflix/concurrency-limits/
 * [avoid_overload]: https://aws.amazon.com/builders-library/using-load-shedding-to-avoid-overload/
 */
internal class ConcurrencyLimitsInterceptor internal constructor(
  private val factory: Factory,
  private val action: Action,
  private val defaultLimiter: Limiter<String>,
  private val clock: Clock,
  private val logLevel: Level
) : NetworkInterceptor {
  /**
   * When this fails, it fails a lot. Log at most one error per minute per node and let the
   * developers use metrics to see what the failure rate is.
   */
  private val durationBetweenErrorsMs = TimeUnit.MINUTES.toMillis(1)
  private var lastErrorLoggedAtMs = -1L

  override fun intercept(chain: NetworkChain) {
    val quotaPath = chain.httpCall.requestHeaders["Quota-Path"]
    val metricsName = quotaPath ?: action.name
    val limiter = when {
      quotaPath != null -> factory.pickLimiter(action, quotaPath)
      else -> defaultLimiter
    }

    val listener: Limiter.Listener? = limiter.acquire(action.name).orElse(null)

    if (listener == null) {
      factory.outcomeCounter.labels(metricsName, "rejected").inc()
      logShedRequest(limiter, quotaPath)
      chain.httpCall.statusCode = HttpURLConnection.HTTP_UNAVAILABLE
      chain.httpCall.takeResponseBody()?.use { sink ->
        sink.writeUtf8("service unavailable")
      }
      return
    }

    if (limiter is AbstractLimiter<*>) {
      factory.limitGauge.labels(metricsName).set(limiter.limit.toDouble())
      factory.inFlightGauge.labels(metricsName).set(limiter.inflight.toDouble())
    }

    try {
      chain.proceed(chain.httpCall)
    } catch (unexpected: Throwable) {
      factory.outcomeCounter.labels(metricsName, "ignored").inc()
      listener.onIgnore()
      throw unexpected
    }

    try {
      when (chain.httpCall.statusCode) {
        in 400 until 500 -> {
          factory.outcomeCounter.labels(metricsName, "ignored").inc()
          listener.onIgnore()
        }
        in 500 until 600 -> {
          factory.outcomeCounter.labels(metricsName, "dropped").inc()
          listener.onDropped() // Count 5XX errors as drops.
        }
        else -> {
          factory.outcomeCounter.labels(metricsName, "success").inc()
          listener.onSuccess()
        }
      }
    } catch (e: IllegalArgumentException) {
      // Service container ignores exception "rtt must be >0 but got 0" here.
      // TODO(jwilson): report this upstream and get it fixed.
      logger.debug { "ignoring concurrency-limits exception: $e" }
    }
  }

  private fun logShedRequest(limiter: Limiter<*>, quotaPath: String?) {
    val nowMs = clock.millis()
    val durationSinceLastErrorMs = nowMs - lastErrorLoggedAtMs
    if (lastErrorLoggedAtMs == -1L || durationSinceLastErrorMs >= durationBetweenErrorsMs) {
      lastErrorLoggedAtMs = nowMs
      logger.log(level = logLevel) {
        "concurrency limits interceptor shedding ${action.name}; " +
          "Quota-Path=$quotaPath; " +
          "inflight=${(limiter as? AbstractLimiter<*>)?.inflight}; " +
          "limit=${(limiter as? AbstractLimiter<*>)?.limit}"
      }
    }
  }

  @Singleton
  class Factory @Inject constructor(
    private val clock: Clock,
    private val limiterFactories: List<ConcurrencyLimiterFactory>,
    private val config: WebConfig,
    metrics: Metrics,
  ) : NetworkInterceptor.Factory {
    val outcomeCounter = metrics.counter(
      name = "concurrency_limits_outcomes",
      help = "what happened in a concurrency limited call?",
      labelNames = listOf("quota_path", "outcome")
    )

    val limitGauge = metrics.gauge(
      name = "concurrency_limits_limit",
      help = "how many calls are permitted at once?",
      labelNames = listOf("quota_path")
    )

    val inFlightGauge = metrics.gauge(
      name = "concurrency_limits_inflight",
      help = "how many calls are currently executing?",
      labelNames = listOf("quota_path")
    )

    /**
     * Note that this cache is application-global. Multiple actions that use the same Quota-Path
     * will be treated as a homogenous group for concurrency limiting.
     */
    private val quotaPathToLimiter: Cache<String, Limiter<String>> = CacheBuilder.newBuilder()
      .build()

    override fun create(action: Action): NetworkInterceptor? {
      if (action.function.findAnnotation<AvailableWhenDegraded>() != null) return null
      return ConcurrencyLimitsInterceptor(
        factory = this,
        action = action,
        defaultLimiter = createLimiterForAction(action, quotaPath = null),
        clock = clock,
        logLevel = config.concurrency_limiter_log_level
      )
    }

    @VisibleForTesting
    internal fun createLimiterForAction(action: Action, quotaPath: String?): Limiter<String> {
      return limiterFactories.asSequence()
        .mapNotNull { it.create(action) }
        .firstOrNull()
        ?: SimpleLimiter.Builder()
          .clock { Duration.between(Instant.EPOCH, clock.instant()).toNanos() }
          .limit(
            VegasLimit.newBuilder()
              // 2 is chosen somewhat arbitrarily here. Most services have one or two endpoints
              // that receive the majority of traffic (power law, yay!), and those endpoints should
              // _start up_ without triggering the concurrency limiter at the parallelism that we
              // configured Jetty to support.
              .initialLimit(config.jetty_max_thread_pool_size / 2)
              .build()
          )
          .named(quotaPath ?: action.name)
          .build()
    }

    internal fun pickLimiter(action: Action, quotaPath: String): Limiter<String> {
      return quotaPathToLimiter.get(quotaPath) { createLimiterForAction(action, quotaPath) }
    }
  }

  private companion object {
    val logger = getLogger<ConcurrencyLimitsInterceptor>()
  }
}
