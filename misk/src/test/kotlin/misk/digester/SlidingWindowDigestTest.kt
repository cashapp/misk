package misk.digester

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SlidingWindowDigestTest {
  //this clock is immutable. SlidingWindowDigest holds a reference to it. Is there another clock that can do this?
  var baseClock: Clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

  @Test
  fun testSlidingWindowDigestEmpty() {

    val digest = newSlidingWindowDigestTest()

    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(Double.NaN)
    expectQuantiles(digest, 0, Double.NaN, emptyMap())
    advanceWindows(1, digest)

    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(Double.NaN)
    expectQuantiles(digest, 0, Double.NaN, emptyMap())
  }

  @Test
  fun testSlidingWindowDigestObservation() {
    val digest = newSlidingWindowDigestTest()

    digest.observe(10.0)
    digest.observe(20.0)
    digest.observe(30.0)

    val windows = windows(digest)

    expectWindowDigests(
        digest.windows.toList(),
        listOf(
            newWindowDigest(windows[0], listOf(10.0, 20.0, 30.0)),
            newWindowDigest(windows[1], listOf(10.0, 20.0, 30.0)),
            newWindowDigest(windows[2], listOf(10.0, 20.0, 30.0))
        )
    )

    // No windows have closed yet so there is no reportable data yet
    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(Double.NaN)

    // Advance time so that one window is now closed
    advanceWindows(1, digest)
    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(30.0)

    expectQuantiles(digest, 3, 60.0,
        mapOf(
            0.25 to 30.0,
            0.5 to 30.0))
  }

  @Test
  fun testSlidingWindowDigestObservationInMultipleWindows() {
    val digest = newSlidingWindowDigestTest()

    val windowsT0_2 = windows(digest)
    digest.observe(10.0) // in t0-t2 buckets

    advanceWindows(1, digest)
    digest.observe(20.0) // in t1-t3 buckets
    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(10.0)

    advanceWindows(1, digest)
    digest.observe(30.0) // in t2-t4 buckets
    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(20.0)

    val windowsT3_5 = advanceWindows(1, digest)
    Assertions.assertThat(digest.quantile(0.5)).isEqualTo(30.0)

    expectWindowDigests(
        digest.windows,
        listOf(
            newWindowDigest(windowsT0_2[0], listOf(10.0)),
            newWindowDigest(windowsT0_2[1], listOf(10.0, 20.0)),
            newWindowDigest(windowsT0_2[2], listOf(10.0, 20.0, 30.0)),
            newWindowDigest(windowsT3_5[0], listOf(20.0, 30.0)),
            newWindowDigest(windowsT3_5[1], listOf(30.0))
        ))
  }

  @Test
  fun testSlidingWindowDigestClosedDigests() {
    val digest = newSlidingWindowDigestTest()
    val windows = windows(digest)

    digest.observe(10.0)

    expectWindowDigests(digest.closedDigests(windows[2].end.plusNanos(1)), listOf())

    expectWindowDigests(digest.closedDigests(windows[2].end), listOf(
      newWindowDigest(windows[2], listOf(10.0))
        ))

    expectWindowDigests(digest.closedDigests(windows[1].end.plusNanos(1)),listOf(
      newWindowDigest(windows[2], listOf(10.0))
      ))

    expectWindowDigests(digest.closedDigests(windows[1].end), listOf(
      newWindowDigest(windows[1], listOf(10.0)),
      newWindowDigest(windows[2], listOf(10.0))
      ))

    expectWindowDigests(digest.closedDigests(windows[0].end.minusNanos(0)),listOf(
      newWindowDigest(windows[0], listOf(10.0)),
      newWindowDigest(windows[1], listOf(10.0)),
      newWindowDigest(windows[2], listOf(10.0))
      ))

  }

  @Test
  fun testSlidingWindowDigestMergeInEmptyToEmpty() {
    val src = newSlidingWindowDigestTest()
    val dest = newSlidingWindowDigestTest()

    advanceWindows(3, src)
    advanceWindows(3, dest)
    dest.mergeIn(src.closedDigests(ZonedDateTime.ofInstant(src.utcNowClock.instant(), ZoneId.of("UTC"))))

    Assertions.assertThat(dest.windows.count()).isEqualTo(0)
  }

  @Test
  fun testSlidingWindowDigestMergeInEmptyToValues() {
    val src = newSlidingWindowDigestTest()
    val dest = newSlidingWindowDigestTest()

    val windowsT0_2 = windows(dest)
    dest.observe(10.0)
    dest.mergeIn(src.closedDigests(ZonedDateTime.ofInstant(src.utcNowClock.instant(), ZoneId.of("UTC"))))

    expectWindowDigests(dest.windows, listOf(
      newWindowDigest(windowsT0_2[0], listOf(10.0)),
      newWindowDigest(windowsT0_2[1], listOf(10.0)),
      newWindowDigest(windowsT0_2[2], listOf(10.0))
        ))
  }

  @Test
  fun testSlidingWindowDigestMergeInValuesToValues() {
    val src = newSlidingWindowDigestTest()
    val dest = newSlidingWindowDigestTest()

    val windowsT0_2 = windows(src)
    src.observe(100.0) // in t0-t2 buckets
    val windowsT3_5 = advanceWindows(3, src)
    src.observe(200.0) // in t3-t5 buckets
    advanceWindows(3, src)

    advanceWindows(1, dest)
    dest.observe(10.0) // in t1-t3 buckets

    Assertions.assertThat(dest.openDigests(false).count()).isEqualTo(3)
    dest.utcNowClock = src.utcNowClock
    dest.mergeIn(src.closedDigests(windowsT0_2[0].end))

    expectWindowDigests(dest.windows, listOf(
      newWindowDigest(windowsT0_2[0], listOf(100.0)),
      newWindowDigest(windowsT0_2[1], listOf(10.0, 100.0)),
      newWindowDigest(windowsT0_2[2], listOf(10.0, 100.0)),
      newWindowDigest(windowsT3_5[0], listOf(10.0, 200.0)),
      newWindowDigest(windowsT3_5[1], listOf(200.0)),
      newWindowDigest(windowsT3_5[2], listOf(200.0)
        )))
  }

  @Test
  fun testSlidingWindowDigestGC() {
    val digest = newSlidingWindowDigestTest()
    digest.observe(10.0)

    // Move just past the threshold for collecting the last window
    digest.utcNowClock = Clock.fixed(windows(digest)[2].end.plusMinutes(1).plusNanos(1).toInstant(), ZoneId.of("UTC"))
    digest.observe(20.0)
    val windows = windows(digest)

   expectWindowDigests(digest.windows, listOf(
      newWindowDigest(windows[0], listOf(20.0)),
      newWindowDigest(windows[1], listOf(20.0)),
      newWindowDigest(windows[2], listOf(20.0))
    ))
  }


  fun setClock(t: ZonedDateTime, digest: SlidingWindowDigest) {
    require(!t.toInstant().isBefore(digest.utcNowClock.instant())) {
      "Cannot go back in time"
    }

    //todo: is there a better way to do this? two sources of truth
    //testClock = Clock.fixed(t.toInstant(), ZoneId.of("UTC"))
    //digest.utcNowClock = testClock
    digest.utcNowClock = Clock.fixed(t.toInstant(), ZoneId.of("UTC"))
  }

  fun windows(slidingWindow: SlidingWindowDigest): List<Window> {
    return slidingWindow.windower.windowsContaining(
        ZonedDateTime.ofInstant(slidingWindow.utcNowClock.instant(), ZoneId.of("UTC")))
  }

  fun advanceWindows(n: Int, digest: SlidingWindowDigest): List<Window> {
    repeat(n) {
      setClock(windows(digest)[0].end, digest)
    }

    return windows(digest)
  }

  fun newWindowDigest(window: Window, values: List<Double>): WindowDigest {
    return WindowDigest(
        window,
        FakeDigest(values)
    )
  }

  fun newSlidingWindowDigestTest(): SlidingWindowDigest {

    return SlidingWindowDigest(
        baseClock,
        Windower(10, 3)
    )
  }

  fun expectWindowDigests(actual: List<WindowDigest>, expected: List<WindowDigest>) {
    Assertions.assertThat(expected.count()).isEqualTo(actual.count())

    for (i in 0 until actual.count()) {
      if (i >= expected.count()) {
        break
      }

      //Compare window of each WindowDigest
      Assertions.assertThat(actual[i].window).isEqualTo(expected[i].window)
      //Compare all values added within TDigest of each WindowDigest
      Assertions.assertThat((actual[i].Digest as FakeDigest).addedValues)
          .isEqualTo((expected[i].Digest as FakeDigest).addedValues)
    }

  }

  fun expectQuantiles(
    digest: SlidingWindowDigest,
    count: Long,
    sum: Double,
    quantileVals: Map<Double, Double>
  ) {

    val snapshot = digest.snapshot(quantileVals.keys.toList()) //should this be keys or values?
    Assertions.assertThat(snapshot.count).isEqualTo(count)
    assertEqualish(sum, snapshot.sum)

    quantileVals.keys.forEachIndexed { i, q ->
      assertEqualish(quantileVals[q], snapshot.quantileVals[i])
    }

  }

  fun assertEqualish(a: Double?, b: Double?) {
    if (a == Double.NaN) {
      Assertions.assertThat(b).isEqualTo(Double.NaN)
    } else {
      Assertions.assertThat(a).isEqualTo(b)
    }
  }
}