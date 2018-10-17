package misk.digester

import java.time.Clock
import java.time.ZonedDateTime

/** WindowDigest holds a t-digest whose data points are scoped to a specific time window. */
data class WindowDigest(
  var window: Window,
  var Digest: TDigest
)

/** Snapshot is the state of a SlidingWindowDigest at a point in time. */
data class Snapshot(
  var quantileVals: DoubleArray,  // Values of specific quantiles.
  var count: Long,                // Count of observations.
  var sum: Double                 // Sum of observations.
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
 * NewSlidingWindowDigest(time.Now, NewDefaultVeneurDigest, NewWindower(60, 6))
 *
 * The following example creates a 10 second sliding window where there are 2 overlapping windows at a given time.
 * Reported quantiles are at most 5 seconds out of date:
 * NewSlidingWindowDigest(time.Now, NewDefaultVeneurDigest, NewWindower(10, 2))
 */
class SlidingWindowDigest constructor(
  var utcNowClock: Clock,
  val windower: Windower
) {

  internal var windows: MutableList<WindowDigest> = mutableListOf()

  /**
   * Adds the given value to all currently open t-openDigests.
   * It is important to note that an observed value is not immediately
   * reflected in calls to Quantile.
   */
  @Synchronized fun observe(value: Double) {
    for (digest in openDigests(true)) {
      digest.Digest.add(value)
    }
  }

  /**
   * Returns estimated value for a quantile. The returned value
   * may not include recently observed values due to how sliding windows are approximated.
   * If no data has been observed then NaN is returned.
   */
  @Synchronized fun quantile(quantile: Double): Double {
    if (windows.count() == 0) { //is this part even necessary?
      return Double.NaN
    }

    val now = ZonedDateTime.now(utcNowClock)
    for (i in windows.count() - 1 downTo 0) {
      if (!now.isBefore(windows[i].window.end)) {
        return windows[i].Digest.quantile(quantile)
      }
    }

    return Double.NaN
  }

  /**
   * Returns a snapshot of estimated values for quantiles, along with the count of observations and their sum.
   * The returned values may not include recent observations due to how sliding windows are approximated.
   * If no data has been observed then a slice of NaNs of having len(quantiles) is returned and NaN is returned for the sum.
   */
  @Synchronized fun snapshot(quantiles: List<Double>): Snapshot {

    val now = ZonedDateTime.now(utcNowClock)

    for (i in windows.count() - 1 downTo 0) {
      if (!now.isBefore(windows[i].window.end)) {

        val quantileVals = DoubleArray(quantiles.count()) { Double.NaN }
        val digest = windows[i].Digest

        quantiles.forEachIndexed { ii, quantile ->
          quantileVals[ii] = digest.quantile(quantile)
        }

        return Snapshot(
            quantileVals,
            digest.count(),
            digest.sum()
        )
      }
    }

    return Snapshot(
        DoubleArray(quantiles.count()) { Double.NaN },
        0,
        Double.NaN
    )
  }

  /**
   * Returns all WindowDigests that ended starting from the given time (inclusive).
   *The returned WindowDigest are ordered by their start time.
   */
  @Synchronized fun closedDigests(from: ZonedDateTime): List<WindowDigest> {

    deleteOlderDigests()

    var wds: MutableList<WindowDigest> = mutableListOf()

    for (wd in windows) {
      if (!from.isAfter(wd.window.end)) {
        wds.add(wd)
      }
    }

    return wds.toList()
  }

  /**
   * Merges in the data from the given WindowDigests.
   * The given windowDigests should use the same windowing boundaries
   * as this; if they do not then quantiles reported by this sliding
   * window digest may be incorrect.
   */
  @Synchronized fun mergeIn(windowDigests: List<WindowDigest>) {
    deleteOlderDigests()

    for (wd in windowDigests) {
      var found = false
      for (existing in windows) {
        if (existing.window.equals(wd.window)) {
          wd.Digest.mergeInto(existing.Digest)
          found = true
          break
        }
      }

      if (!found) {
        val newDigest = WindowDigest(
            wd.window,
            FakeDigest() //should this be some kind of custom new tDigest function?
        )
        windows.add(newDigest)
        wd.Digest.mergeInto(newDigest.Digest)
      }
    }

    windows = windows.asSequence().sortedWith(compareBy { it.window.start }).toMutableList()
  }

  /** Deletes openDigests with windows that ended more than 1 minute ago. */
  private fun deleteOlderDigests() {
    val now = ZonedDateTime.now(utcNowClock)

    val deleteBefore = now.minusMinutes(1)
    var firstIndex = 0

    for (window in windows) {
      if (deleteBefore.isAfter(window.window.end)) {
        firstIndex++
      } else {
        break
      }
    }

    if (firstIndex > 0) {
      windows = windows.subList(firstIndex, windows.count())
    }

  }

  /**
   * Returns all WindowDigests that are currently open, creating new windows if necessary.
   * Older openDigests that ended more than 1 minute earlier are discarded if gc is true.
   */
  private fun openDigests(gc: Boolean): List<WindowDigest> {
    val now = ZonedDateTime.now(utcNowClock)

    if (gc) {
      deleteOlderDigests()
    }

    var localWindows = windower.windowsContaining(now)

    val digests: MutableList<WindowDigest> = mutableListOf()

    for (newWindow in localWindows) {
      var found = false
      for (existingWindow in windows) {
        if (existingWindow.window.equals(newWindow)) {
          found = true
          digests.add(existingWindow)
          break
        }
      }

      if (!found) {
        val newDigest = WindowDigest(
            newWindow,
            FakeDigest() //should this be some kind of custom new tDigest function? This will need to become a TDigest through guice to support Veneur Digest?
        )
        windows.add(newDigest)
        digests.add(newDigest)
      }
    }

    windows = windows.asSequence().sortedWith(compareBy { it.window.start }).toMutableList()

    return digests
  }
}