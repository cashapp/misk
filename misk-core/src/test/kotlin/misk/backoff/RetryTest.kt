package misk.backoff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertFailsWith

internal class RetryTest {
  @Test fun retries() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

    val result = retry(3, backoff) { retry ->
      if (retry < 2) throw IllegalStateException("this failed")
      "succeeded on retry $retry"
    }

    assertThat(result).isEqualTo("succeeded on retry 2")
  }

  @Test fun failsIfExceedsMaxRetries() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

    assertFailsWith<IllegalStateException> {
      retry(3, backoff) { throw IllegalStateException("this failed") }
    }
  }

  @Test fun honorsBackoff() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

    assertFailsWith<IllegalStateException> {
      retry(3, backoff) { throw IllegalStateException("this failed") }
    }

    // Backoff should have advanced
    assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(40))
  }

  @Test fun resetsBackoffPriorToUse() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))

    // Preseed the backoff with a delay
    backoff.nextRetry()
    backoff.nextRetry()
    backoff.nextRetry()

    assertFailsWith<IllegalStateException> {
      retry(3, backoff) { throw IllegalStateException("this failed") }
    }

    // resets backoff prior to use
    assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(40))
  }

  @Test fun resetsBackoffAfterSuccess() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
    val result = retry(3, backoff) { retry ->
      if (retry < 2) throw IllegalStateException("this failed")
      "hello"
    }

    assertThat(result).isEqualTo("hello")

    // Backoff should be reset to base delay after success
    assertThat(backoff.nextRetry()).isEqualTo(Duration.ofMillis(10))
  }

  @Test fun onlyRetryExceptionIfInOnlyRetryList() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
    val result = retry(3, backoff, listOf(IllegalStateException())) { retry ->
      if (retry < 2) throw IllegalStateException("this failed")
      "hello"
    }

    assertThat(result).isEqualTo("hello")
  }

  @Test fun notRetryExceptionIfNotInOnlyRetryList() {
    val backoff = ExponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
    assertFailsWith<IllegalStateException> {
      retry(3, backoff, listOf(IndexOutOfBoundsException())) { retry ->
        if (retry < 2) throw IllegalStateException("this failed")
        "hello"
      }
    }
  }
}
