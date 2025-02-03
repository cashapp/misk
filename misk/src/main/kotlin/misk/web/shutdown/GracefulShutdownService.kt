package misk.web.shutdown

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.web.GracefulShutdownConfig
import misk.web.WebConfig
import misk.web.shutdown.GracefulShutdownService.Companion.ShutdownReason.Idle
import misk.web.shutdown.GracefulShutdownService.Companion.ShutdownReason.Interrupt
import misk.web.shutdown.GracefulShutdownService.Companion.ShutdownReason.MaxWait
import org.jetbrains.annotations.VisibleForTesting
import wisp.logging.Tag
import wisp.logging.getLogger
import wisp.logging.withTags
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Singleton
internal class GracefulShutdownService @Inject constructor(
  webConfig: WebConfig,
  clock: Clock,
) : AbstractIdleService() {
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
    return inFlightRequestsTracker.decrementAndGet()
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

    val initialInFlight = inFlightRequests
    var shutdownReason = Idle
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
        shutdownReason = MaxWait
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
        shutdownReason = Interrupt
        break
      }

      // Update timestamps for next evaluation.
      idleFor = idleSince.get().elapsedNow()
      untilIdle = idleTimeout - idleFor
      untilMaxShutdown = maxShutdownWait - shutdownStarted.elapsedNow()
    }

    val shutdownWait = shutdownStarted.elapsedNow().inWholeMilliseconds.milliseconds
    withTags(
      Tag("misk.graceful.result.reason", shutdownReason),
      Tag("misk.graceful.result.delay", shutdownWait.delayBucket()),
      Tag("misk.graceful.result.start_inflight", initialInFlight.inFlightBucket()),
      Tag("misk.graceful.result.end_inflight", inFlightRequests.inFlightBucket()),
      Tag("misk.graceful.config.idle_timeout", gracefulShutdownConfig.idle_timeout),
      Tag("misk.graceful.config.max_graceful_wait", gracefulShutdownConfig.max_graceful_wait),
      Tag("misk.graceful.config.rejection_status_code", gracefulShutdownConfig.rejection_status_code)
    ) {
      logger.info { "Graceful Shutdown proceeding after $shutdownWait " +
        "with $inFlightRequests in-flight requests" }
    }
  }

  companion object {
    private val logger = getLogger<GracefulShutdownService>()

    private enum class ShutdownReason {
      Idle,
      MaxWait,
      Interrupt,
    }

    /**
     * Bucket delays so they can be visualized as a time series from logs.
     */
    @VisibleForTesting
    internal fun Duration.delayBucket(): Long =
      when {
        inWholeMilliseconds <= 500 -> inWholeMilliseconds.toNearest(100)
        inWholeMilliseconds <= 10_000 -> inWholeMilliseconds.toNearest(500)
        else -> inWholeMilliseconds.toNearest(1000)
      }

    /**
     * Bucket in-flight so they can be visualized as a time series from logs.
     */
    @VisibleForTesting
    internal fun Long.inFlightBucket(): Long =
      when {
        this <= 5 -> this
        this <= 100 -> this.toNearest(10)
        this <= 500 -> this.toNearest(50)
        else -> this.toNearest(100)
      }

    /**
     * Round to the nearest multiple of [multiple]
     */
    private fun Long.toNearest(multiple: Int): Long =
      multiple * (toDouble() / multiple).roundToLong()

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
