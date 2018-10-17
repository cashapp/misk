package misk.digester

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SlidingWindowDigestTest {
  //this clock is immutable. SlidingWindowDigest holds a reference to it. Is there another clock that can do this?
  var testClock: Clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

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
    
  }

  fun setClock(t: ZonedDateTime, digest: SlidingWindowDigest) {
    require(!t.toInstant().isBefore(testClock.instant())) {
      "Cannot go back in time"
    }


    //todo: is there a better way to do this? two sources of truth
    testClock = Clock.fixed(t.toInstant(), ZoneId.of("UTC"))
    digest.utcNowClock = testClock
  }

  fun windows(slidingWindow: SlidingWindowDigest): List<Window> {
    return slidingWindow.windower.windowsContaining(
        ZonedDateTime.ofInstant(testClock.instant(), ZoneId.of("UTC")))
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
        testClock,
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