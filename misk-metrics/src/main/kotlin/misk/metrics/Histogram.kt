package misk.metrics

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Skeleton for the functionality of histograms
 *
 * A histogram samples observations (usually things like request durations or response sizes)
 * and counts them in configurable buckets.
 *
 * A sample implementation can be found in PrometheusHistogram
 */
interface Histogram {
  /** records a new set of labels and accompanying duration */
  fun record(duration: Double, vararg labelValues: String)

  /** returns the number of buckets */
  fun count(vararg labelValues: String): Int

  /** records a new set of labels and the time to execute the work lambda in milliseconds */
  fun <T> timedMills(vararg labelValues: String, work: () -> T): T {
    val (time, result) = TimedFunctions.timed { work.invoke() }
    record(time.toMillis().toDouble(), *labelValues)
    return result
  }
}

/** TODO(jwilson): move misk.concurrent out of the core package and depend on this. */
private object TimedFunctions {
  fun <T> timed(f: () -> T) = timed(Ticker.systemTicker(), f)

  fun <T> timed(ticker: Ticker, f: () -> T): Pair<Duration, T> {
    val stopwatch = Stopwatch.createStarted(ticker)
    val result = f()
    return Duration.ofMillis(stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)) to result
  }
}