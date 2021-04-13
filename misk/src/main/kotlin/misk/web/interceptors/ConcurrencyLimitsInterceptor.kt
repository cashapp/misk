package misk.web.interceptors

import com.netflix.concurrency.limits.Limiter
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import misk.Action
import misk.exceptions.StatusCode
import misk.logging.log
import misk.web.AvailableWhenDegraded
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import org.slf4j.event.Level
import wisp.logging.getLogger
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

/**
 * Detects degraded behavior and sheds requests accordingly. Internally this uses adaptive limiting
 * as implemented by Netflix's [concurrency-limits][concurrency_limits] library.
 *
 * This annotation is applied to all actions by default. Opt-out with [AvailableWhenDegraded].
 *
 * [concurrency_limits]: https://github.com/Netflix/concurrency-limits/
 */
internal class ConcurrencyLimitsInterceptor internal constructor(
  private val actionName: String,
  private val limiter: Limiter<String>,
  private val clock: Clock
) : NetworkInterceptor {
  /**
   * When this fails, it fails a lot. Log at most one error per minute per node and let the
   * developers use metrics to see what the failure rate is.
   */
  private val durationBetweenErrorsMs = TimeUnit.MINUTES.toMillis(1)
  private var lastErrorLoggedAtMs = -1L

  override fun intercept(chain: NetworkChain) {
    val listener: Limiter.Listener? = limiter.acquire(actionName).orElse(null)

    if (listener == null) {
      logShedRequest()
      chain.httpCall.statusCode = StatusCode.SERVICE_UNAVAILABLE.code
      chain.httpCall.takeResponseBody()?.use { sink ->
        sink.writeUtf8("service unavailable")
      }
      return
    }

    try {
      chain.proceed(chain.httpCall)
    } catch (unexpected: Throwable) {
      listener.onIgnore()
      throw unexpected
    }

    try {
      when (chain.httpCall.statusCode) {
        in 400 until 500 -> listener.onIgnore()
        in 500 until 600 -> listener.onDropped() // Count 5XX errors as drops.
        else -> listener.onSuccess()
      }
    } catch (e: IllegalArgumentException) {
      // Service container does a ignores an RTT == 0 exception here.
      // TODO(jwilson): report this upstream and get it fixed.
      logger.debug { "ignoring concurrency-limits exception: $e" }
    }
  }

  private fun logShedRequest() {
    val nowMs = clock.millis()
    val durationSinceLastErrorMs = nowMs - lastErrorLoggedAtMs
    var level = Level.INFO
    if (lastErrorLoggedAtMs == -1L || durationSinceLastErrorMs >= durationBetweenErrorsMs) {
      lastErrorLoggedAtMs = nowMs
      level = Level.ERROR
    }
    logger.log(level = level) { "concurrency limits interceptor shedding $actionName" }
  }

  @Singleton
  class Factory @Inject constructor(
    private val clock: Clock,
    private val limiterFactories: List<ConcurrencyLimiterFactory>,
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      if (action.function.findAnnotation<AvailableWhenDegraded>() != null) return null

      val limiter = limiterFactories.asSequence()
        .mapNotNull { it.create(action) }
        .firstOrNull()
        ?: SimpleLimiter.Builder()
          .clock { clock.millis() }
          .build()

      return ConcurrencyLimitsInterceptor(action.name, limiter, clock)
    }
  }

  private companion object {
    val logger = getLogger<ConcurrencyLimitsInterceptor>()
  }
}
