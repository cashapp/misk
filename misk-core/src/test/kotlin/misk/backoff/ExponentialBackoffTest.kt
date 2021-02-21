package misk.backoff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class ExponentialBackoffTest {
  @Test fun unjitteredExponentialBackoff() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofSeconds(1))

    assertThat(backoff.nextRetry().toMillis()).isEqualTo(10L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(20L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(40L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(80L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(160L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(320L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(640L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(1000L) // max delay
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(1000L)
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(1000L)

    backoff.reset()
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(10L)
  }

  @Test fun jitteredExponentialBackoff() {
    val backoff = ExponentialBackoff(
      Duration.ofMillis(10),
      Duration.ofSeconds(1),
      Duration.ofMillis(10)
    )

    assertThat(backoff.nextRetry().toMillis()).isBetween(10L, 20L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(20L, 30L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(40L, 50L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(80L, 90L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(160L, 170L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(320L, 330L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(640L, 650L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1000L, 1010L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1000L, 1010L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1000L, 1010L)

    backoff.reset()
    assertThat(backoff.nextRetry().toMillis()).isBetween(10L, 20L)
  }

  @Test fun dynamicJitteredExponentialBackoff() {
    val baseDelay = AtomicLong(10)
    val maxDelay = AtomicLong(1000L)
    val jitter = AtomicLong(10)

    val backoff = ExponentialBackoff(
      { Duration.ofMillis(baseDelay.get()) },
      { Duration.ofMillis(maxDelay.get()) }
    ) { Duration.ofMillis(jitter.get()) }

    assertThat(backoff.nextRetry().toMillis()).isBetween(10L, 20L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(20L, 30L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(40L, 50L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(80L, 90L)

    // Adjust and ensure new parameters are taken into account
    baseDelay.set(20L)
    maxDelay.set(2000L)
    jitter.set(100L)

    assertThat(backoff.nextRetry().toMillis()).isBetween(320L, 500L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(640L, 1700L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(1280L, 1380L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(2000L, 2100L)
    assertThat(backoff.nextRetry().toMillis()).isBetween(2000L, 2100L)
  }

  @Test fun maxRetriesCapped() {
    val baseDelay = AtomicLong(100)
    val maxDelay = AtomicLong(5000L)
    val backoff = ExponentialBackoff(
      { Duration.ofMillis(baseDelay.get()) },
      { Duration.ofMillis(maxDelay.get()) }
    ) { Duration.ofMillis(0) }

    // Previously the number of retries was uncapped and it would cause a long overflow at 57
    // iterations.
    for (i in 0 until 100 + 1) {
      backoff.nextRetry().toMillis()
    }
    assertThat(backoff.nextRetry().toMillis()).isEqualTo(5000L)
  }
}
