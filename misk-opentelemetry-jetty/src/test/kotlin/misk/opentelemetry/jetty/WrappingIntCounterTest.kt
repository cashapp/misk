package misk.opentelemetry.jetty

import java.util.function.LongSupplier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WrappingIntCounterTest {
  private var nowMs = 0L
  private var statisticsDurationMs = 3_600_000L

  private fun counter(duration: LongSupplier = LongSupplier { statisticsDurationMs }) =
    WrappingIntCounter(duration, LongSupplier { nowMs })

  /** Advances both clocks as if [ms] passed with no reset. */
  private fun elapse(ms: Long) {
    nowMs += ms
    statisticsDurationMs += ms
  }

  /** Restarts the statistics [agoMs] before the next read, [sinceReadMs] after the previous one. */
  private fun reset(sinceReadMs: Long = 10_000, agoMs: Long = 2_000) {
    nowMs += sinceReadMs
    statisticsDurationMs = agoMs
  }

  @Test
  fun `stays monotonic past int overflow and full 32-bit wrap`() {
    val counter = counter()

    assertThat(counter.extend { Int.MAX_VALUE }).isEqualTo(2_147_483_647L)
    elapse(10_000)
    // Jetty's (int) cast reads negative past 2^31.
    assertThat(counter.extend { Int.MIN_VALUE }).isEqualTo(2_147_483_648L)
    elapse(10_000)
    assertThat(counter.extend { -1 }).isEqualTo(4_294_967_295L)
    elapse(10_000)
    // Past 2^32 the raw value restarts near zero.
    assertThat(counter.extend { 5 }).isEqualTo(4_294_967_301L)
  }

  @Test
  fun `statistics reset carries the prior total instead of decreasing or counting a wrap`() {
    val counter = counter()

    assertThat(counter.extend { 1_000 }).isEqualTo(1_000L)
    reset()
    assertThat(counter.extend { 3 }).isEqualTo(1_003L)
    elapse(10_000)
    assertThat(counter.extend { 10 }).isEqualTo(1_010L)
  }

  @Test
  fun `a reset is carried even when the new total has caught up`() {
    val counter = counter()

    assertThat(counter.extend { 3 }).isEqualTo(3L)
    reset()
    assertThat(counter.extend { 5 }).isEqualTo(8L)
  }

  @Test
  fun `a reset from the upper half is not a wrap`() {
    val counter = counter()

    assertThat(counter.extend { Int.MIN_VALUE }).isEqualTo(2_147_483_648L)
    reset()
    assertThat(counter.extend { 3 }).isEqualTo(2_147_483_651L)
  }

  @Test
  fun `a reset while the total is read rereads it from the new statistics`() {
    val counter = counter()

    assertThat(counter.extend { 1_000 }).isEqualTo(1_000L)
    nowMs += 10_000
    var reads = 0
    // The first read returns the pre-reset total, then Jetty restarts the duration and clears the totals.
    val total =
      counter.extend {
        if (reads++ == 0) {
          statisticsDurationMs = 0
          1_200
        } else {
          40
        }
      }
    assertThat(total).isEqualTo(1_040L)
    assertThat(reads).isEqualTo(2)
  }

  @Test
  fun `a drop from below 2^31 the duration check missed is a reset, not a 2^32 wrap`() {
    val counter = counter()

    assertThat(counter.extend { 1_000 }).isEqualTo(1_000L)
    elapse(10_000)
    assertThat(counter.extend { 3 }).isEqualTo(1_003L)
  }

  @Test
  fun `a pause after sampling the duration is not a reset of young statistics`() {
    statisticsDurationMs = 1_000
    val counter = counter()

    assertThat(counter.extend { 100 }).isEqualTo(100L)
    elapse(10_000)
    // A 2s pause (GC, descheduling) while reading the total: the duration was sampled at 11s, before it.
    assertThat(
        counter.extend {
          elapse(2_000)
          105
        }
      )
      .isEqualTo(105L)
  }

  @Test
  fun `a backward wall-clock step is not a reset`() {
    val counter = counter()

    assertThat(counter.extend { 1_000 }).isEqualTo(1_000L)
    // Jetty 11 measures the statistics duration on the wall clock, so a 30s step back shortens both together.
    elapse(10_000)
    nowMs -= 30_000
    statisticsDurationMs -= 30_000
    assertThat(counter.extend { 1_002 }).isEqualTo(1_002L)
  }

  @Test
  fun `a backward wall-clock step while sampling the duration is reread, not a reset`() {
    var rollBack = false
    val counter =
      counter(
        LongSupplier {
          // The clock is corrected back 2s after the first clock read and before Jetty computes the duration.
          if (rollBack) {
            rollBack = false
            nowMs -= 2_000
            statisticsDurationMs -= 2_000
          }
          statisticsDurationMs
        }
      )

    assertThat(counter.extend { 100 }).isEqualTo(100L)
    elapse(10_000)
    rollBack = true
    assertThat(counter.extend { 105 }).isEqualTo(105L)
  }

  @Test
  fun `a backward wall-clock step is not a reset of young statistics`() {
    statisticsDurationMs = 1_000
    val counter = counter()

    assertThat(counter.extend { 100 }).isEqualTo(100L)
    // After 10s the clock is corrected back 2s: the statistics look 9s old although 10s passed.
    elapse(10_000)
    nowMs -= 2_000
    statisticsDurationMs -= 2_000
    assertThat(counter.extend { 105 }).isEqualTo(105L)
  }

  @Test
  fun `a new handler's totals continue from the carried total`() {
    val counter = counter()

    assertThat(counter.extend { 7 }).isEqualTo(7L)
    counter.carry()
    elapse(10_000)
    assertThat(counter.extend { 2 }).isEqualTo(9L)
  }

  @Test
  fun `totals that resets leave untouched only handle wraps`() {
    val counter = WrappingIntCounter.neverReset()

    assertThat(counter.extend { 7 }).isEqualTo(7L)
    nowMs += 10_000
    assertThat(counter.extend { 9 }).isEqualTo(9L)
  }
}
