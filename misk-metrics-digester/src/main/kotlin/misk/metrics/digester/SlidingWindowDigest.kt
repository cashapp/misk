package misk.metrics.digester

import java.time.Clock
import java.time.ZonedDateTime

/** WindowDigest holds a t-digest whose data points are scoped to a specific time window. */
data class WindowDigest<T : TDigest<T>>(
  val window: Window,
  val digest: T
)

/** Snapshot is the state of a SlidingWindowDigest at a point in time. */
data class Snapshot(
  val quantileVals: List<Double>, // Values of specific quantiles.
  val count: Long, // Count of observations.
  val sum: Double // Sum of observations.
)

/**
 * SlidingWindowDigest approximates quantiles of data for a trailing time period. It is thread-safe.
 *
 * To efficiently store observed data in a mergeable way, t-openDigests are used.
 * As t-openDigests do not support discarding older data points, the sliding window
 * aspect is approximated by keeping multiple separate t-openDigests scoped to discrete
 * overlapping time windows. As a result, quantile data is reported from the most recent window that has ended.
 *
 * The following example creates a 1 minute sliding window where there are 6 overlapping windows at a given time.
 * Reported quantiles are at most 10 seconds out of date.
 * SlidingWindowDigest(Windower(60, 6),  fun() = VeneurDigest())
 *
 * The following example creates a 10 second sliding window where there are 2 overlapping windows at a given time.
 * Reported quantiles are at most 5 seconds out of date:
 * NewSlidingWindowDigest(Windower(10, 2),  fun() = VeneurDigest())
 */
class SlidingWindowDigest<T : TDigest<T>> constructor(
  internal val windower: Windower,
  internal val tDigest: () -> T,
  private val utcNowClock: Clock = Clock.systemUTC()
) {

  internal val windows: MutableList<WindowDigest<T>> = mutableListOf()
  /**
   * Adds the given value to all currently open t-openDigests.
   * It is important to note that an observed value is not immediately
   * reflected in calls to Quantile.
   */
  @Synchronized fun observe(value: Double) {
    for (digest in openDigests(true)) {
      digest.digest.add(value)
    }
  }

  /**
   * Returns estimated value for a quantile. The returned value
   * may not include recently observed values due to how sliding windows are approximated.
   * If no data has been observed then NaN is returned.
   */
  @Synchronized fun quantile(quantile: Double): Double {
    val now = ZonedDateTime.now(utcNowClock)
    for (i in windows.count() - 1 downTo 0) {
      if (!now.isBefore(windows[i].window.end)) {
        return windows[i].digest.quantile(quantile)
      }
    }

    return Double.NaN
  }

  /**
   * Returns a snapshot of estimated values for quantiles, along with the count of observations and their sum.
   * The returned values may not include recent observations due to how sliding windows are approximated.
   * If no data has been observed then a slice of NaNs of having quantiles.count() is returned and NaN is returned for the sum.
   */
  @Synchronized fun snapshot(quantiles: List<Double>): Snapshot {
    val now = ZonedDateTime.now(utcNowClock)
    for (i in windows.count() - 1 downTo 0) {
      if (!now.isBefore(windows[i].window.end)) {
        val quantileVals = MutableList(quantiles.count()) { Double.NaN }
        val digest = windows[i].digest
        quantiles.forEachIndexed { ii, quantile ->
          quantileVals[ii] = digest.quantile(quantile)
        }
        return Snapshot(quantileVals, digest.count(), digest.sum())
      }
    }
    return Snapshot(
        MutableList(quantiles.count()) { Double.NaN },
        0,
        Double.NaN
    )
  }

  /**
   * Returns all WindowDigests that ended starting from the given time (inclusive).
   *The returned WindowDigest are ordered by their start time.
   */
  @Synchronized fun closedDigests(from: ZonedDateTime): List<WindowDigest<T>> {
    deleteOlderDigests()
    return windows.filter { !from.isAfter(it.window.end) }
  }

  /**
   * Merges in the data from the given WindowDigests.
   * The given windowDigests should use the same windowing boundaries
   * as this; if they do not then quantiles reported by this sliding
   * window digest may be incorrect.
   */
  @Synchronized fun mergeIn(windowDigests: List<WindowDigest<T>>) {
    deleteOlderDigests()
    for (wd in windowDigests) {
      val existing = windows.find { it.window == wd.window }
      if (existing != null) {
        wd.digest.mergeInto(existing.digest)
      } else {
        val newDigest = WindowDigest(wd.window, tDigest())
        windows.add(newDigest)
        wd.digest.mergeInto(newDigest.digest)
      }
    }
    windows.sortBy { it.window.start }
  }

  /** Deletes openDigests with windows that ended more than 1 minute ago. */
  private fun deleteOlderDigests() {
    val now = ZonedDateTime.now(utcNowClock)
    val deleteBefore = now.minusMinutes(1)

    windows.retainAll { !deleteBefore.isAfter(it.window.end) }
  }

  /**
   * Returns all WindowDigests that are currently open, creating new windows if necessary.
   * Older openDigests that ended more than 1 minute earlier are discarded if gc is true.
   */
  fun openDigests(gc: Boolean): List<WindowDigest<T>> {
    val now = ZonedDateTime.now(utcNowClock)
    if (gc) {
      deleteOlderDigests()
    }

    val digests = windower.windowsContaining(now).map { window: Window ->
      val existing = windows.find { it.window == window }
      if (existing == null) {
        val newDigest = WindowDigest(window, tDigest())
        windows.add(newDigest)
        newDigest
      } else {
        existing
      }
    }

    windows.sortBy { it.window.start }
    return digests
  }
}
