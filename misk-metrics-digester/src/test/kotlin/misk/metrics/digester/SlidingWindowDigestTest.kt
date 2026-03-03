package misk.metrics.digester

import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.SortedMap
import java.util.concurrent.TimeUnit

class SlidingWindowDigestTest {

  @Test
  fun slidingWindowDigestEmpty() {
    val digest = SlidingWindowDigestTestingSuite()
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualToHonorNan(Double.NaN)
    digest.expectQuantiles(0, Double.NaN, sortedMapOf())
    digest.advanceWindows(1)
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualToHonorNan(Double.NaN)
    digest.expectQuantiles(0, Double.NaN, sortedMapOf())
  }

  @Test
  fun slidingWindowDigestObservation() {
    val digest = SlidingWindowDigestTestingSuite()
    digest.slidingWindowDigest.observe(10.0)
    digest.slidingWindowDigest.observe(20.0)
    digest.slidingWindowDigest.observe(30.0)
    val windows = digest.windows()
    expectWindowDigests(
        digest.slidingWindowDigest.windows,
        listOf(
            newWindowDigest(windows[0], listOf(10.0, 20.0, 30.0)),
            newWindowDigest(windows[1], listOf(10.0, 20.0, 30.0)),
            newWindowDigest(windows[2], listOf(10.0, 20.0, 30.0))
        )
    )
    // No windows have closed yet so there is no reportable data yet
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualToHonorNan(Double.NaN)
    // Advance time so that one window is now closed
    digest.advanceWindows(1)
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualTo(30.0)
    digest.expectQuantiles(3, 60.0,
        sortedMapOf(
            0.25 to 30.0,
            0.5 to 30.0))
  }

  @Test
  fun slidingWindowDigestObservationInMultipleWindows() {
    val digest = SlidingWindowDigestTestingSuite()
    val windowsT0_2 = digest.windows()
    digest.slidingWindowDigest.observe(10.0) // in t0-t2 buckets
    digest.advanceWindows(1)
    digest.slidingWindowDigest.observe(20.0) // in t1-t3 buckets
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualTo(10.0)
    digest.advanceWindows(1)
    digest.slidingWindowDigest.observe(30.0) // in t2-t4 buckets
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualTo(20.0)
    val windowsT3_5 = digest.advanceWindows(1)
    assertThat(digest.slidingWindowDigest.quantile(0.5)).isEqualTo(30.0)
    expectWindowDigests(
        digest.slidingWindowDigest.windows,
        listOf(
            newWindowDigest(windowsT0_2[0], listOf(10.0)),
            newWindowDigest(windowsT0_2[1], listOf(10.0, 20.0)),
            newWindowDigest(windowsT0_2[2], listOf(10.0, 20.0, 30.0)),
            newWindowDigest(windowsT3_5[0], listOf(20.0, 30.0)),
            newWindowDigest(windowsT3_5[1], listOf(30.0))
        ))
  }

  @Test
  fun slidingWindowDigestClosedDigests() {
    val digest = SlidingWindowDigestTestingSuite()
    val windows = digest.windows()
    digest.slidingWindowDigest.observe(10.0)
    expectWindowDigests(digest.slidingWindowDigest.closedDigests(windows[2].end.plusNanos(1)),
        listOf())
    expectWindowDigests(digest.slidingWindowDigest.closedDigests(windows[2].end), listOf(
        newWindowDigest(windows[2], listOf(10.0))
    ))
    expectWindowDigests(digest.slidingWindowDigest.closedDigests(windows[1].end.plusNanos(1)),
        listOf(
            newWindowDigest(windows[2], listOf(10.0))
        ))
    expectWindowDigests(digest.slidingWindowDigest.closedDigests(windows[1].end), listOf(
        newWindowDigest(windows[1], listOf(10.0)),
        newWindowDigest(windows[2], listOf(10.0))
    ))
    expectWindowDigests(digest.slidingWindowDigest.closedDigests(windows[0].end.minusNanos(0)),
        listOf(
            newWindowDigest(windows[0], listOf(10.0)),
            newWindowDigest(windows[1], listOf(10.0)),
            newWindowDigest(windows[2], listOf(10.0))
        ))
  }

  @Test
  fun slidingWindowDigestMergeInEmptyToEmpty() {
    val src = SlidingWindowDigestTestingSuite()
    val dest = SlidingWindowDigestTestingSuite()
    src.advanceWindows(3)
    dest.advanceWindows(3)
    dest.slidingWindowDigest.mergeIn(
        src.slidingWindowDigest.closedDigests(
            ZonedDateTime.ofInstant(src.baseClock.instant(), ZoneId.of("UTC"))))
    assertThat(dest.slidingWindowDigest.windows.count()).isEqualTo(0)
  }

  @Test
  fun slidingWindowDigestMergeInEmptyToValues() {
    val src = SlidingWindowDigestTestingSuite()
    val dest = SlidingWindowDigestTestingSuite()
    val windowsT0_2 = dest.windows()
    dest.slidingWindowDigest.observe(10.0)
    dest.slidingWindowDigest.mergeIn(
        src.slidingWindowDigest.closedDigests(
            ZonedDateTime.ofInstant(src.baseClock.instant(), ZoneId.of("UTC"))))
    expectWindowDigests(dest.slidingWindowDigest.windows, listOf(
        newWindowDigest(windowsT0_2[0], listOf(10.0)),
        newWindowDigest(windowsT0_2[1], listOf(10.0)),
        newWindowDigest(windowsT0_2[2], listOf(10.0))
    ))
  }

  @Test
  fun slidingWindowDigestMergeInValuesToValues() {
    val src = SlidingWindowDigestTestingSuite()
    val dest = SlidingWindowDigestTestingSuite()
    val windowsT0_2 = src.windows()
    src.slidingWindowDigest.observe(100.0) // in t0-t2 buckets
    val windowsT3_5 = src.advanceWindows(3)
    src.slidingWindowDigest.observe(200.0) // in t3-t5 buckets
    src.advanceWindows(3)
    dest.advanceWindows(1)
    dest.slidingWindowDigest.observe(10.0) // in t1-t3 buckets
    assertThat(dest.slidingWindowDigest.openDigests(false).count()).isEqualTo(3)
    dest.slidingWindowDigest.mergeIn(src.slidingWindowDigest.closedDigests(windowsT0_2[0].end))
    expectWindowDigests(dest.slidingWindowDigest.windows, listOf(
        newWindowDigest(windowsT0_2[0], listOf(100.0)),
        newWindowDigest(windowsT0_2[1], listOf(10.0, 100.0)),
        newWindowDigest(windowsT0_2[2], listOf(10.0, 100.0)),
        newWindowDigest(windowsT3_5[0], listOf(10.0, 200.0)),
        newWindowDigest(windowsT3_5[1], listOf(200.0)),
        newWindowDigest(windowsT3_5[2], listOf(200.0)
        )))
  }

  @Test
  fun slidingWindowDigestGC() {
    val digest = SlidingWindowDigestTestingSuite()
    digest.slidingWindowDigest.observe(10.0)
    // Move just past the threshold for collecting the last window
    digest.baseClock.setNow(digest.windows()[2].end.toInstant())
    digest.baseClock.add(1, TimeUnit.MINUTES)
    digest.baseClock.add(1, TimeUnit.MILLISECONDS)

    digest.slidingWindowDigest.observe(20.0)
    val windows = digest.windows()
    expectWindowDigests(digest.slidingWindowDigest.windows, listOf(
        newWindowDigest(windows[0], listOf(20.0)),
        newWindowDigest(windows[1], listOf(20.0)),
        newWindowDigest(windows[2], listOf(20.0))
    ))
  }

  fun newWindowDigest(window: Window, values: List<Double>): WindowDigest<FakeDigest> {
    return WindowDigest(
        window,
        FakeDigest(values)
    )
  }

  fun expectWindowDigests(
    actual: List<WindowDigest<FakeDigest>>,
    expected: List<WindowDigest<FakeDigest>>
  ) {
    assertThat(expected.count()).isEqualTo(actual.count())
    for (i in 0 until actual.count()) {
      if (i >= expected.count()) {
        break
      }
      // Compare window of each WindowDigest
      assertThat(actual[i].window).isEqualTo(expected[i].window)
      // Compare all values added within TDigest of each WindowDigest
      assertThat(actual[i].digest.addedValues).isEqualTo(expected[i].digest.addedValues)
    }
  }
}

class SlidingWindowDigestTestingSuite {
  val baseClock: FakeClock = FakeClock()

  val slidingWindowDigest = SlidingWindowDigest(
      Windower(10, 3),
      fun() = FakeDigest(),
      baseClock
  )

  fun setClock(t: ZonedDateTime) {
    require(!t.toInstant().isBefore(baseClock.instant())) {
      "Cannot go back in time"
    }

    baseClock.setNow(t.toInstant())
  }

  fun windows(): List<Window> {
    return slidingWindowDigest.windower.windowsContaining(
        ZonedDateTime.ofInstant(baseClock.instant(), ZoneId.of("UTC")))
  }

  fun advanceWindows(n: Int): List<Window> {
    repeat(n) {
      setClock(windows()[0].end)
    }
    return windows()
  }

  fun expectQuantiles(count: Long, sum: Double, quantileVals: SortedMap<Double, Double>) {
    val snapshot =
        slidingWindowDigest.snapshot(quantileVals.keys.toList())
    assertThat(snapshot.count).isEqualTo(count)
    assertThat(snapshot.sum).isEqualToHonorNan(sum)
    assertThat(snapshot.quantileVals).isEqualTo(quantileVals.values.toList())
  }
}
