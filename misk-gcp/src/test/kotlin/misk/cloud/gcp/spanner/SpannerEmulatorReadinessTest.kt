package misk.cloud.gcp.spanner

import com.google.cloud.spanner.ErrorCode
import com.google.cloud.spanner.SpannerExceptionFactory
import java.util.concurrent.ExecutionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

/**
 * Covers the startup retries in [GoogleSpannerEmulator]. The emulator itself needs Docker and a race that does not
 * reproduce on demand, so these exercise the decision the retries turn on instead.
 */
class SpannerEmulatorReadinessTest {
  // No backoff, few attempts: the policy is under test here, not the sleeping.
  private fun <T> retry(attempts: Int = 3, block: () -> T): T =
    SpannerEmulatorReadiness.retryWhileNotReady(
      description = "do the thing",
      attempts = attempts,
      backoffBaseMillis = 0L,
      backoffMaxMillis = 0L,
      block = block,
    )

  private fun notReady() = SpannerExceptionFactory.newSpannerException(ErrorCode.UNAVAILABLE, "io exception")

  @Test
  fun `an unavailable emulator is not ready`() {
    assertThat(SpannerEmulatorReadiness.isNotReady(notReady())).isTrue()
  }

  @Test
  fun `a deadline exceeded emulator is not ready`() {
    val failure = SpannerExceptionFactory.newSpannerException(ErrorCode.DEADLINE_EXCEEDED, "too slow")

    assertThat(SpannerEmulatorReadiness.isNotReady(failure)).isTrue()
  }

  /**
   * The failure a caller actually sees. `OperationFuture.get` wraps the Spanner error, so a check that only looked at
   * the top-level exception would miss every real occurrence and the fix would silently stop working.
   */
  @Test
  fun `an unavailable emulator wrapped in an ExecutionException is not ready`() {
    val wrapped = ExecutionException("createInstance failed", notReady())

    assertThat(SpannerEmulatorReadiness.isNotReady(wrapped)).isTrue()
  }

  @Test
  fun `a missing instance is not a readiness failure`() {
    val failure = SpannerExceptionFactory.newSpannerException(ErrorCode.NOT_FOUND, "no such instance")

    assertThat(SpannerEmulatorReadiness.isNotReady(failure)).isFalse()
  }

  @Test
  fun `an unrelated failure is not a readiness failure`() {
    assertThat(SpannerEmulatorReadiness.isNotReady(IllegalArgumentException("nope"))).isFalse()
  }

  @Test
  fun `retries until the emulator is ready`() {
    var calls = 0

    val result = retry {
      calls++
      if (calls < 3) throw notReady()
      "created"
    }

    assertThat(result).isEqualTo("created")
    assertThat(calls).isEqualTo(3)
  }

  /** A real error must surface on the first attempt rather than being retried and masked. */
  @Test
  fun `rethrows a failure that is not about readiness`() {
    var calls = 0

    assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
      retry {
        calls++
        throw IllegalArgumentException("nope")
      }
    }

    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun `gives up once the attempts run out, and keeps the last failure`() {
    var calls = 0
    val lastFailure = notReady()

    assertThatExceptionOfType(IllegalStateException::class.java)
      .isThrownBy {
        retry {
          calls++
          throw lastFailure
        }
      }
      .withMessageContaining("did not do the thing in time")
      .withCause(lastFailure)

    assertThat(calls).isEqualTo(3)
  }
}
