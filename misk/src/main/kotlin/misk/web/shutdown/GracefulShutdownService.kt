package misk.web.shutdown

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.metrics.v2.Metrics
import misk.web.GracefulShutdownConfig
import misk.web.WebConfig
import wisp.logging.getLogger
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Singleton
internal class GracefulShutdownService @Inject constructor(
  webConfig: WebConfig,
  metrics: Metrics,
  clock: Clock,
) : AbstractIdleService() {
  private val shutdownDurationMetric = metrics.histogram(
    "graceful_shutdown_wait_duration_ms",
    "duration to wait for in-flight requests and idle timeout",
  )

  private val shutdownInFlightRequestsMetric = metrics.gauge(
    "graceful_shutdown_in_flight_requests",
    "number of inflight requests during graceful shutdown"
  )

  private val shutdownRejectedRequestsMetric = metrics.counter(
    "graceful_shutdown_rejected_requests_total",
    "number of requests rejected during graceful shutdown"
  )

  @OptIn(ExperimentalMiskApi::class)
  private val gracefulShutdownConfig =
    webConfig.graceful_shutdown_config ?:
      GracefulShutdownConfig(disabled = true)

  private val timeSource = JavaClockTimeSource(clock)

  /**
   * The amount of time that we need to have not received an incoming request.
   */
  private val idleTimeout : Duration = max(0, gracefulShutdownConfig.idle_timeout).milliseconds

  /**
   * The maximum time we will wait for the service to be idle and in-flight requests to complete.
   */
  private val maxShutdownWait : Duration = if (gracefulShutdownConfig.max_graceful_wait <= 0) {
    Duration.INFINITE
  } else {
    gracefulShutdownConfig.max_graceful_wait.milliseconds
  }

  /**
   * The last incoming request we have seen.
   */
  private var idleSince: AtomicReference<TimeMark> = AtomicReference(timeSource.markNow())

  /**
   * Tracker of in-flight requests from the interceptor.  We will only shutdown when this reaches
   * 0, along with the idle timeout.
   */
  private val inFlightRequestsTracker = AtomicLong(0L)

  /**
   * Current number of in-flight requests.
   */
  internal val inFlightRequests: Long
    get() = inFlightRequestsTracker.get()

  /**
   * NoOp until shutdown then GracefulReject.  Even if we shut down we still want to the
   * interceptor to continue responding with 503.
   */
  internal var shuttingDown: Boolean = false
    private set

  /**
   * Report a rejected request, this is not in-flight, but resets the idle timeout wait.
   */
  internal fun reportReject() {
    idleSince.set(timeSource.markNow())
    shutdownRejectedRequestsMetric.inc()
  }

  /**
   * Report an in-flight request, this resets the idle timeout wait and will delay shutdown until
   * reportRequestComplete is called and total in-flight requests reach 0.
   */
  internal fun reportRequest(): Long {
    idleSince.set(timeSource.markNow())
    return inFlightRequestsTracker.incrementAndGet()
  }

  /**
   * Report an in-flight request has completed, if the in-flight requests count reaches 0 the
   * service can proceed with shutdown once incoming idle timeout is reached.
   */
  internal fun reportRequestComplete(): Long {
    val currentInFlightRequests = inFlightRequestsTracker.decrementAndGet()
    if (shuttingDown) {
      shutdownInFlightRequestsMetric.set(currentInFlightRequests.toDouble())
    }
    return currentInFlightRequests
  }

  override fun startUp() {
    shuttingDown = false
  }

  override fun shutDown() {
    shuttingDown = true

    if (gracefulShutdownConfig.disabled) {
      // The service should not be installed when disabled. Just in case, do nothing.
      return
    }

    shutdownInFlightRequestsMetric.set(inFlightRequests.toDouble())

    val shutdownStarted = timeSource.markNow()
    var idleFor = idleSince.get().elapsedNow()
    var untilIdle = idleTimeout - idleFor
    var untilMaxShutdown = maxShutdownWait

    // Loop until in-flight requests reach 0 and we have been idle for longer than the idle timeout
    while (
      inFlightRequestsTracker.get() != 0L ||
      idleFor < idleTimeout
    ) {
      // Break if we have hit the max delay regardless of the current state.  This may result in
      // a non-graceful shutdown.
      if (untilMaxShutdown <= Duration.ZERO) {
        break
      }

      // If time until max shutdown is less than remaining idle wait, sleep for max shutdown.
      // If we're not idle, sleep until for remaining time until idle.
      // Otherwise, sleep for 250ms to monitor the in flight requests until idle.
      val sleepFor =
        if (untilMaxShutdown < untilIdle) {
          untilMaxShutdown
        } else if (idleFor < idleTimeout) {
          untilIdle
        } else {
          240.milliseconds
        } + 10.milliseconds // Add a buffer for sleep/clock precision.

      try {
        Thread.sleep(sleepFor.inWholeMilliseconds)
      } catch (_: InterruptedException) {
        logger.info { "Exiting for interrupt."}
        break
      }

      // Update timestamps for next evaluation.
      idleFor = idleSince.get().elapsedNow()
      untilIdle = idleTimeout - idleFor
      untilMaxShutdown = maxShutdownWait - shutdownStarted.elapsedNow()
    }

    val shutdownWait = shutdownStarted.elapsedNow().inWholeMilliseconds
    shutdownDurationMetric.observe(shutdownWait.toDouble())
    logger.info { "Graceful Shutdown proceeding after " + "${shutdownWait.milliseconds}" }
  }

  companion object {
    private val logger = getLogger<GracefulShutdownService>()

    /**
     * Simple wrapper around java Clock to use kotlin TimeSource methods.
     */
    private class JavaClockTimeSource(private val clock: Clock) : TimeSource {
      override fun markNow(): TimeMark = object : TimeMark {
        private val instant = clock.instant()
        override fun elapsedNow(): Duration =
          clock.instant().toEpochMilli().milliseconds - instant.toEpochMilli().milliseconds
      }
    }
  }
}
