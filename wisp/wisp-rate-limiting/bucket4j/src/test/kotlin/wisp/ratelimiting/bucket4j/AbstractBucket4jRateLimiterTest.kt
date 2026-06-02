package wisp.ratelimiting.bucket4j

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.RateLimiterMetrics
import wisp.ratelimiting.testing.TestRateLimitConfig
import wisp.time.FakeClock

abstract class AbstractBucket4jRateLimiterTest<T> {
  abstract val rateLimiter: RateLimiter
  protected val fakeClock = FakeClock()
  protected val meterRegistry = SimpleMeterRegistry()
  private val metrics = RateLimiterMetrics(meterRegistry)

  private val consumedMetrics by lazy {
    metrics.consumptionAttempts(TestRateLimitConfig, RateLimiterMetrics.ConsumptionResult.SUCCESS)
  }

  private val rejectedMetrics by lazy {
    metrics.consumptionAttempts(TestRateLimitConfig, RateLimiterMetrics.ConsumptionResult.REJECTED)
  }

  private val exceptionMetrics by lazy {
    metrics.consumptionAttempts(TestRateLimitConfig, RateLimiterMetrics.ConsumptionResult.EXCEPTION)
  }

  private val totalConsumed by lazy { metrics.tokensConsumed(TestRateLimitConfig) }

  private val availableDuration by lazy { metrics.limitAvailabilityDuration(TestRateLimitConfig) }

  private val consumptionDuration by lazy { metrics.limitConsumptionDuration(TestRateLimitConfig) }

  private val releaseDuration by lazy { metrics.limitReleaseDuration(TestRateLimitConfig) }

  private val resetDuration by lazy { metrics.limitResetDuration(TestRateLimitConfig) }

  private val testDuration by lazy { metrics.limitTestDuration(TestRateLimitConfig) }

  @Test
  fun `can take tokens up to limit`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      with(result) {
        assertThat(didConsume).isTrue()
        assertThat(remaining).isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(4L - it)
      }
      assertThat(consumedMetrics.count()).isEqualTo(it.toDouble() + 1.0)
      assertThat(rejectedMetrics.count()).isZero()
      assertThat(exceptionMetrics.count()).isZero()
      // Note - we test the durations with count because mock proxy operations are often sub 1ms,
      // making the duration ms zero
      assertThat(consumptionDuration.count()).isGreaterThan(0)
      // We never tried to test consumption, so this should be zero
      assertThat(testDuration.count()).isZero()
    }

    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      assertThat(didConsume).isFalse()
      assertThat(remaining).isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isZero()
    }
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()
  }

  @Test
  fun `testConsumptionAttempt does not affect bucket state`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val initiallyAvailable = rateLimiter.availableTokens(KEY, TestRateLimitConfig)
      val testConsumptionResult = rateLimiter.testConsumptionAttempt(KEY, TestRateLimitConfig)
      val availableAfterTest = rateLimiter.availableTokens(KEY, TestRateLimitConfig)
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      assertThat(initiallyAvailable)
        .isEqualTo(availableAfterTest)
        // Bucket4j estimation returns the actual amount remaining, not the amount remaining if
        // test consumption had been a real consumption
        .isEqualTo(testConsumptionResult.remaining)
      with(result) {
        assertThat(didConsume).isEqualTo(testConsumptionResult.couldHaveConsumed).isTrue()
        assertThat(remaining).isEqualTo(4L - it)
      }
      // Test consumptions should not affect metric emission
      assertThat(consumedMetrics.count()).isEqualTo(it.toDouble() + 1.0)
      assertThat(rejectedMetrics.count()).isZero()
      assertThat(exceptionMetrics.count()).isZero()
    }

    val testConsumptionResult = rateLimiter.testConsumptionAttempt(KEY, TestRateLimitConfig)
    assertThat(testConsumptionResult.couldHaveConsumed).isFalse()
    assertThat(testConsumptionResult.remaining).isZero()
    assertThat(availableDuration.count()).isGreaterThan(0)
    assertThat(consumptionDuration.count()).isGreaterThan(0)
    assertThat(testDuration.count()).isGreaterThan(0)
  }

  @Test
  fun `withToken respects limits`() {
    var counter = 0
    repeat((TestRateLimitConfig.capacity * 2).toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    // Should have consumed capacity tokens, then been rejected for capacity tokens
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(exceptionMetrics.count()).isZero()
    assertThat(consumptionDuration.count()).isGreaterThan(0)
    assertThat(releaseDuration.count()).isZero()
    // We should have been able to increment the counter until we consumed the bucket
    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)
  }

  @Test
  fun `bucket is refilled on schedule`() {
    var counter = 0
    repeat(TestRateLimitConfig.capacity.toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isZero()
    assertThat(exceptionMetrics.count()).isZero()

    val result = rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ }
    assertThat(result.consumptionData.didConsume).isFalse()
    assertThat(result.result).isNull()
    assertThat(result.consumptionData.remaining)
      .isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig))
      .isZero()

    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()

    // Elapse enough time that the next request refills the bucket
    fakeClock.add(TestRateLimitConfig.refillPeriod)
    repeat(TestRateLimitConfig.capacity.toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    assertThat(counter).isEqualTo(10).isEqualTo(TestRateLimitConfig.capacity * 2)

    assertThat(consumedMetrics.count()).isEqualTo(10.0)
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()
  }

  @Test
  fun `resetting the bucket permits consumption of full capacity`() {
    var counter = 0
    repeat(TestRateLimitConfig.capacity.toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isZero()
    assertThat(exceptionMetrics.count()).isZero()

    val result = rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ }
    assertThat(result.consumptionData.didConsume).isFalse()
    assertThat(result.result).isNull()
    assertThat(result.consumptionData.remaining)
      .isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig))
      .isZero()

    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()

    // Reset the bucket to refill it entirely
    rateLimiter.resetBucket(KEY, TestRateLimitConfig)
    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(TestRateLimitConfig.capacity)
    repeat(TestRateLimitConfig.capacity.toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    assertThat(counter).isEqualTo(10).isEqualTo(TestRateLimitConfig.capacity * 2)

    assertThat(consumedMetrics.count()).isEqualTo(10.0)
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()
    assertThat(resetDuration.count()).isGreaterThan(0)
  }

  @Test
  fun `exception metrics are tracked`() {
    var counter = 0
    rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ }
    assertThat(counter).isOne()
    assertThat(consumedMetrics.count()).isOne()
    assertThat(rejectedMetrics.count()).isZero()
    assertThat(exceptionMetrics.count()).isZero()

    setException(RuntimeException())
    assertThrows<RuntimeException> { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    assertThat(counter).isOne()
    assertThat(consumedMetrics.count()).isOne()
    assertThat(rejectedMetrics.count()).isZero()
    assertThat(exceptionMetrics.count()).isOne()
  }

  @Test
  fun `total consumed is accurate`() {
    rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    rateLimiter.consumeToken(KEY, TestRateLimitConfig, 3)

    // Three successful consumption attempts
    assertThat(consumedMetrics.count()).isEqualTo(3.0)
    // Five total tokens consumed
    assertThat(totalConsumed.count()).isEqualTo(5.0)
  }

  abstract fun setException(e: RuntimeException)

  abstract fun removeException()

  companion object {
    private const val KEY = "test_key"
  }
}
