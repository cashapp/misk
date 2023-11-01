package wisp.ratelimiting.bucket4j

import io.github.bucket4j.mock.ProxyManagerMock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig
import wisp.time.FakeClock

abstract class AbstractBucket4jRateLimiterTest<T> {
  abstract val rateLimiter: RateLimiter
  protected val fakeClock = FakeClock()

  @Test
  fun `can take tokens up to limit`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      with(result) {
        Assertions.assertThat(didConsume).isTrue()
        Assertions.assertThat(remaining).isEqualTo(4L - it)
      }
    }
    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      Assertions.assertThat(didConsume).isFalse()
      Assertions.assertThat(remaining).isZero()
    }
  }

  @Test
  fun `withToken respects limits`() {
    var counter = 0
    repeat((TestRateLimitConfig.capacity * 2).toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    // We should have been able to increment the counter until we consumed the bucket
    Assertions.assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)
  }

  @Test
  fun `bucket is refilled on schedule`() {
    var counter = 0
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }

    val result = rateLimiter.withToken(KEY, TestRateLimitConfig) {
      counter++
    }
    Assertions.assertThat(result.consumptionData.didConsume).isFalse()
    Assertions.assertThat(result.result).isNull()
    Assertions.assertThat(result.consumptionData.remaining).isZero()
    Assertions.assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    // Elapse enough time that the next request refills the bucket
    fakeClock.add(TestRateLimitConfig.refillPeriod)
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    Assertions.assertThat(counter).isEqualTo(10)
  }

  companion object {
    private const val KEY = "test_key"
  }
}
