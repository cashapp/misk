package misk.sampling

import misk.concurrent.FakeTicker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class RateLimiterTest {
  private val ticker = FakeTicker()
  private val rateLimiter = RateLimiter.Factory(ticker, ticker).create(1L)

  @Test
  fun `rate zero`() {
    rateLimiter.permitsPerSecond = 0L
    assertThat(rateLimiter.tryAcquire(1L, 0, TimeUnit.MILLISECONDS)).isFalse()
  }

  @Test
  fun `cannot acquire zero permits`() {
    val exception = assertFailsWith<IllegalArgumentException> {
      rateLimiter.tryAcquire(0L, 0, TimeUnit.MILLISECONDS)
    }
    assertThat(exception).hasMessage("unexpected permitCount: 0")
  }

  @Test
  fun `cannot use negative timeout`() {
    val exception = assertFailsWith<IllegalArgumentException> {
      rateLimiter.tryAcquire(1L, -1L, TimeUnit.MILLISECONDS)
    }
    assertThat(exception).hasMessage("unexpected timeout: -1")
  }

  @Test
  fun `consume slower than target rate`() {
    rateLimiter.permitsPerSecond = 2L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 1_000)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 1_000, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 1_000)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 1_000, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 1_000)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 1_000, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(500L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 1_000)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 1_000, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(1_000L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 1_000L)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 1_000, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(1_500L)
  }

  @Test
  fun `consume at target rate`() {
    rateLimiter.permitsPerSecond = 2L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 500)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 500, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 500)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 500, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 500)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 500, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(500L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 500)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 500, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(1_000L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 500)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 500, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(1_500L)
  }

  @Test
  fun `consumer faster than target rate`() {
    rateLimiter.permitsPerSecond = 2L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 499)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 499, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 499)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 499, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 499)).isEqualTo(0L)
    assertThat(rateLimiter.tryAcquire(1L, 499, TimeUnit.MILLISECONDS)).isFalse()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 499)).isEqualTo(0L)
    assertThat(rateLimiter.tryAcquire(1L, 499, TimeUnit.MILLISECONDS)).isFalse()
    assertThat(ticker.nowMs).isEqualTo(0L)
  }

  @Test
  fun `rate limit increases`() {
    rateLimiter.permitsPerSecond = 2L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    // Exhausted.
    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(0L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isFalse()
    assertThat(ticker.nowMs).isEqualTo(0L)

    rateLimiter.permitsPerSecond = 10L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(100L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(1L)
  }

  @Test
  fun `rate limit decreases`() {
    rateLimiter.permitsPerSecond = 10L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(10L)
    assertThat(rateLimiter.tryAcquire(10L, 100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(100L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(1L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(ticker.nowMs).isEqualTo(200L)

    rateLimiter.permitsPerSecond = 2L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 100)).isEqualTo(0L)
    assertThat(rateLimiter.tryAcquire(1L, 100, TimeUnit.MILLISECONDS)).isFalse()
    assertThat(ticker.nowMs).isEqualTo(200L)
  }

  @Test fun `permit count exceeds window size`() {
    rateLimiter.permitsPerSecond = 2L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 2_000)).isEqualTo(2L)
    assertThat(rateLimiter.tryAcquire(3L, 2_000, TimeUnit.MILLISECONDS)).isFalse()
    assertThat(ticker.nowMs).isEqualTo(0L)

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 2_000)).isEqualTo(2L)
  }

  @Test fun `QPS is set to 0`(){
    rateLimiter.permitsPerSecond = 0L

    assertThat(rateLimiter.getPermitsRemaining(TimeUnit.MILLISECONDS, 2_000)).isEqualTo(0L)
    assertThat(rateLimiter.tryAcquire(1L, 0, TimeUnit.MILLISECONDS)).isFalse()
  }
}
