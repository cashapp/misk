package misk.opentelemetry.jetty

import java.util.function.LongSupplier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResetCarryingCounterTest {
  private var nowMs = 0L
  private var statisticsDurationMs = 60_000L
  private val counter = ResetCarryingCounter(LongSupplier { statisticsDurationMs }, LongSupplier { nowMs })

  private fun elapse(ms: Long) {
    nowMs += ms
    statisticsDurationMs += ms
  }

  @Test
  fun `statistics reset carries the prior total so the sum never decreases`() {
    assertThat(counter.extend { 2.5 }).isEqualTo(2.5)
    elapse(10_000)
    assertThat(counter.extend { 4.0 }).isEqualTo(4.0)
    // Restarted 1s before this read.
    nowMs += 10_000
    statisticsDurationMs = 1_000
    assertThat(counter.extend { 0.5 }).isEqualTo(4.5)
    elapse(10_000)
    assertThat(counter.extend { 1.0 }).isEqualTo(5.0)
  }

  @Test
  fun `a reset is carried even when the new total has caught up`() {
    assertThat(counter.extend { 3.0 }).isEqualTo(3.0)
    nowMs += 10_000
    statisticsDurationMs = 1_000
    assertThat(counter.extend { 5.0 }).isEqualTo(8.0)
  }

  @Test
  fun `a dip without a reset holds the total instead of carrying it`() {
    assertThat(counter.extend { 100.0 }).isEqualTo(100.0)
    // Jetty 11 times come from the wall clock; a step back during in-flight requests records negative samples.
    elapse(10_000)
    assertThat(counter.extend { 99.0 }).isEqualTo(100.0)
    elapse(10_000)
    assertThat(counter.extend { 105.0 }).isEqualTo(105.0)
  }
}
