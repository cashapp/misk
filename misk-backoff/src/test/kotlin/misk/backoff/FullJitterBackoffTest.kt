package misk.backoff

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FullJitterBackoffTest {
  @Test
  fun fullJitteredBackoff() {
    val backoff =
      ExponentialBackoff(Duration.ofMillis(10), Duration.ofSeconds(1)) { curDelayMs: Long ->
        Duration.ofMillis(curDelayMs / 2 + 1)
      }

    assertThat(backoff.nextRetry().toMillis()).isBetween(10L, 20L + 10L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(20L, 30L + 20L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(40L, 50L + 40L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(80L, 90L + 80L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(160L, 170L + 160L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(320L, 330L + 320L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(640L, 650L + 640L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1000L, 1010L + 1000L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1000L, 1010L + 1000L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1000L, 1010L + 1000L)

    backoff.reset()
    assertThat(backoff.nextRetry().toMillis()).isBetween(10L, 20L + 10L)
  }
}
