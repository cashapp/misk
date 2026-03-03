package misk.time

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import java.time.Duration

/** Fails unless the block executes in the expected time. */
fun assertElapsedTime(
  expected: Duration,
  tolerance: Duration = Duration.ofMillis(100),
  block: () -> Unit
) {
  val startNanos = System.nanoTime()
  block()
  val endNanos = System.nanoTime()
  val duration = Duration.ofNanos(endNanos - startNanos)

  assertThat(duration.toMillis().toDouble())
      .isCloseTo(expected.toMillis().toDouble(), Offset.offset(tolerance.toNanos().toDouble()))
}
