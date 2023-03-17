package misk.perf

import com.google.common.base.Ticker
import com.google.common.util.concurrent.AbstractExecutionThreadService
import io.prometheus.client.Summary
import misk.concurrent.Sleeper
import misk.metrics.v2.Metrics
import misk.metrics.v2.PeakGauge
import org.slf4j.event.Level
import wisp.logging.getLogger
import wisp.logging.info
import java.time.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects and records pauses experienced by the VM. Garbage collection is a common source of
 * pauses, but pauses can be caused by many things: CPU throttling, excessive swapping, excessive
 * active threads, etc.
 *
 * Heavily inspired by [jhiccup](https://www.azulsystems.com/jHiccup) which is
 * [public domain](https://creativecommons.org/publicdomain/zero/1.0/)
 */
@Singleton
internal class PauseDetector @Inject constructor(
  private val config: PauseDetectorConfig,
  private val ticker: Ticker,
  private val sleeper: Sleeper,
  val metrics: Metrics,
) : AbstractExecutionThreadService() {

  /** Log levels by pause time sorted by severity descending*/
  private val logLevels: List<LogLevels> = listOf(
    LogLevels(config.logErrorMillis, Level.ERROR),
    LogLevels(config.logWarnMillis, Level.WARN),
    LogLevels(config.logInfoMillis, Level.INFO)
  )

  /**
   * Tracks the distribution and total amount of pause time observed.
   *
   * (Using a summary instead of a histogram to prefer more accurate node-local data over
   * more accurate aggregations)
   */
  private val pauseSummary: Summary =
    metrics.summary("jvm_pause_time_summary_ms", "Summary in ms of pause time", listOf())

  // TODO: delete me
  private val pauseSum = metrics.gauge("jvm_pause_time_sum", "Sum of pause time in ms", listOf())

  private val pauseCounter = metrics.counter(
    "jvm_pause_detector_count",
    "Counter tracking the number of pause detection iterations",
    listOf()
  )

  /** Tracks peak pause time. */
  private val pausePeak: PeakGauge =
    metrics.peakGauge("jvm_pause_time_peak_ms", "Peak gauge of pause time", listOf())

  private val pauseDetectionTime = metrics.gauge(
    name = "jvm_pause_detection_time",
    "Time in milliseconds of the last pause detection",
    listOf()
  )

  private val pauseDetectionSelfNsecs = metrics.counter(
    "jvm_pause_detector_self_nsecs",
    "Sum of recorded self-time of the pause detector (nanoseconds)"
  )

  // No synchronization is necessary for these variables: they are only ever accessed by the
  // detector thread itself OR by a test harness thread.
  //
  // (They are also initialized in the constructor, but that initialization has a happens-before
  // relationship to the [Thread.start] that starts the thread).
  /** Used to prevent generating early noise (for example in the first few seconds of startup) */
  private var shortestObservedDeltaTimeNsec = Long.MAX_VALUE
  /** Tracks the start time of the last invocation to [sleep] */
  private var startTimeNsec: Long = 0
  private var lastSecond: Long = 0
  private var invocationsSinceLastSecond: Int = 0
  private var lastSelfTimeNsecs: Double = 0.0
  private var lastPauseSum: Double = 0.0

  override fun run() {
    while (isRunning) {
      // NB: Broken into two different functions so that we can inject time delays for testing
      // purposes.
      sleep()
      check()
    }
  }

  /**
   * Sleeps for the configured resolution
   *
   * NB: [sleep] and [check] must always be invoked from the same thread.
   */
  internal fun sleep() {
    startTimeNsec = ticker.read()
    if (config.resolutionMillis != 0L) {
      sleeper.sleep(Duration.ofMillis(config.resolutionMillis))
    }
  }

  /**
   * Check the elapsed time after an invocation to [sleep]. Elapsed time beyond the configured
   * resolution and the shortest observed delta is recorded as pause time.
   *
   * NB: [sleep] and [check] must always be invoked from the same thread.
   */
  internal fun check(): PauseResults {
    val stopTimeNsec: Long = ticker.read()
    val deltaTimeNsec: Long = (stopTimeNsec - startTimeNsec)

    // We expect a monotonic ticker that should measure at least the resolution time between
    // invocations. Guard against a non-monotonic or otherwise "fast" ticker from polluting results.
    if (deltaTimeNsec < MILLISECONDS.toNanos(config.resolutionMillis)) {
      val decreaseMs = config.resolutionMillis - NANOSECONDS.toMillis(deltaTimeNsec)
      logger.info("Observed a negative pause time of ${decreaseMs}ms. Non-monotonic ticker?")
    } else if (deltaTimeNsec < shortestObservedDeltaTimeNsec) {
      shortestObservedDeltaTimeNsec = deltaTimeNsec
    }

    val stopSecond = NANOSECONDS.toSeconds(stopTimeNsec)
    if (stopSecond > lastSecond) {
      val currentSum = pauseSum.get()
      val observedPause = currentSum - lastPauseSum
      val shortestBounding =
        NANOSECONDS.toMillis(
          invocationsSinceLastSecond * (shortestObservedDeltaTimeNsec - MILLISECONDS.toNanos(
            1
          ))
        )
      val currentSelfTimeNsecs = pauseDetectionSelfNsecs.get()
      val observedSelfTimeMillis =
        NANOSECONDS.toMillis((currentSelfTimeNsecs - lastSelfTimeNsecs).toLong())
      val unaccounted =
        1000 - observedPause - observedSelfTimeMillis - invocationsSinceLastSecond - shortestBounding

      logger.info(
        "pauseDetectionsLastSecond" to invocationsSinceLastSecond,
        "pauseMillis" to observedPause,
        "pauseSelf" to observedSelfTimeMillis,
      ) { "pause=$observedPause ms (${(observedPause / 10)}%), pauseSum=$currentSum," +
          " invocations=$invocationsSinceLastSecond, pauseSelfTime=$currentSelfTimeNsecs," +
          " shortestNsecs=$shortestObservedDeltaTimeNsec," +
          " shortestBounding=$shortestBounding ms, unaccounted=$unaccounted ms" }
      invocationsSinceLastSecond = 0
      lastSecond = stopSecond
      lastPauseSum = currentSum
      lastSelfTimeNsecs = currentSelfTimeNsecs
    }
    invocationsSinceLastSecond++

    val pauseTimeNsec = deltaTimeNsec - shortestObservedDeltaTimeNsec
    val pauseMillis = NANOSECONDS.toMillis(pauseTimeNsec)
    if (pauseMillis >= config.metricsUpdateFloor) {
      val pauseMillisDouble = pauseMillis.toDouble()
      pauseSummary.observe(pauseMillisDouble)
      pauseSum.inc(pauseMillisDouble)
      pausePeak.record(pauseMillisDouble)
    }

    val level = getLoggingLevel(pauseMillis)
    if (level != null) {
      val message = "Detected JVM pause of $pauseMillis ms"
      when (level) {
        Level.INFO -> logger.info(message)
        Level.WARN -> logger.warn(message)
        Level.ERROR -> logger.error(message)
        else -> { // NOOP
        }
      }
    }

    val pauseResults = PauseResults(
      pauseTimeMillis = pauseMillis,
      shortestObservedDeltaNsec = shortestObservedDeltaTimeNsec
    )

    // Always increment the counter and detection time regardless of the detected pause time.
    pauseCounter.inc()
    pauseDetectionTime.set(NANOSECONDS.toMillis(stopTimeNsec).toDouble())
    val selfTime = (ticker.read() - stopTimeNsec).coerceAtLeast(0)
    pauseDetectionSelfNsecs.inc(selfTime.toDouble())

    return pauseResults
  }

  private fun getLoggingLevel(pauseMillis: Long) : Level? {
    return logLevels.firstOrNull { it.limitMillis in 0..pauseMillis }?.level
  }

  data class PauseResults(val pauseTimeMillis: Long, val shortestObservedDeltaNsec: Long)

  data class LogLevels(val limitMillis: Long, val level: Level)

  companion object {
    private val logger = getLogger<PauseDetector>()
  }
}
