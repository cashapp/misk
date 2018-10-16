package misk.digester

import java.time.ZonedDateTime

class SlidingWindowDigest constructor(
  private val windower: Windower,
  private val windows: MutableList<WindowDigest>
) {

 // var windower: Windower
 // var windows: MutableList<WindowDigest>
 // var shuffle: (MutableList<Int>)

  //synchronized methods
  //countdown latches for each of the variables (problem with a permenate lock)
  //synchronized data accessors for each of hte variables

  data class WindowDigest(
    var window: Window,
    var Digest: VeneurDigest) //?is it veneur digest????????

  data class Snapshot(
    var quantiles: DoubleArray, // Values of specific quantiles.
    var count: Long,             // Count of observations.
    var sum: Double              // Sum of observations.
  )

  @Synchronized fun observe(value: Double) {

    for (digest in openDigests(true)) {
      digest
    }
  }

  @Synchronized fun quantile(quantile: Double): Double {
    if (windows.count() == 0) { //is this part even necessary?
      return Double.NaN
    }

    val now = ZonedDateTime.now()
    for (i in windows.count()-1 downTo 0) {
      if (!now.isBefore(windows[i].window.end)) {
        return windows[i].Digest.mergingDigest().quantile(quantile)
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

    val now = ZonedDateTime.now()

    for (i in windows.count()-1 downTo 0) {
      if (!now.isBefore(windows[i].window.end)) {

        val quantileVals = DoubleArray(quantiles.count()) { Double.NaN }
        val digest = windows[i].Digest

        quantiles.forEachIndexed { i, quantile ->
          quantileVals[i] = digest.mergingDigest().quantile(quantile)
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

  @Synchronized fun MergeIn(windowDigests: List<WindowDigest>) {
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
              newDigestFn()
          )
          windows.add(newDigest)
          wd.Digest.mergeInto(newDigest.Digest)
        }
      }
    }

    sort.Slice(s.windows, func(i, j int) bool {
      return s.windows[i].Start.Before(s.windows[j].Start)
    })
  }

  fun deleteOlderDigests(val now: ZonedDateTime) {
    val now = ZonedDateTime.now()

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

  fun openDigests(gc: Boolean): List<WindowDigest> {
    val now = ZonedDateTime.now()
    if (gc) {
      deleteOlderDigests(now)
    }

    var localWindows = windower.windowContaining(now)

    val digests: MutableList<WindowDigest> = mutableListOf()

    for (newWindow in localWindows) {
      var found = false
      for (existingWindow in windows) {
        if (existingWindow.equals(newWindow)) {
          found = true
          digests.add(existingWindow)
          break
        }
      }

      if (!found) {
        val newDigest = WindowDigest(newWindow, newDigestFn())
        windows.add(newDigest)
        digests.add(newDigest)
      }
    }

    /*if !sort.SliceIsSorted(s.windows, func(i, j int) bool {
      return s.windows[i].Start.Before(s.windows[j].Start)
    }) {
      panic("internal windows are not sorted as expected")
    }*/

    return digests
  }
}