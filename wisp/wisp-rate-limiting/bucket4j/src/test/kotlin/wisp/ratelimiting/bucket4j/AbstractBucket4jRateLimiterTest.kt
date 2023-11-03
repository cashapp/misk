package wisp.ratelimiting.bucket4j

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.RateLimiterMetrics
import wisp.ratelimiting.testing.TestRateLimitConfig
import wisp.time.FakeClock

abstract class AbstractBucket4jRateLimiterTest<T> {
  abstract val rateLimiter: RateLimiter
  private val collectorRegistry = CollectorRegistry()
  protected val fakeClock = FakeClock()
  protected val prometheusRegistry = PrometheusMeterRegistry(
    PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
  )
  private val metrics = RateLimiterMetrics(prometheusRegistry)

  private val consumedMetrics by lazy {
    metrics.consumptionAttempts(
      TestRateLimitConfig,
      RateLimiterMetrics.ConsumptionResult.SUCCESS
    )
  }

  private val rejectedMetrics by lazy {
    metrics.consumptionAttempts(
      TestRateLimitConfig,
      RateLimiterMetrics.ConsumptionResult.REJECTED
    )
  }

  private val exceptionMetrics by lazy {
    metrics.consumptionAttempts(
      TestRateLimitConfig,
      RateLimiterMetrics.ConsumptionResult.EXCEPTION
    )
  }

  private val totalConsumed by lazy {
    metrics.tokensConsumed(TestRateLimitConfig)
  }

  @Test
  fun `can take tokens up to limit`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      with(result) {
        assertThat(didConsume).isTrue()
        assertThat(remaining).isEqualTo(4L - it)
      }
      assertThat(consumedMetrics.count()).isEqualTo(it.toDouble() + 1.0)
      assertThat(rejectedMetrics.count()).isZero()
      assertThat(exceptionMetrics.count()).isZero()
    }

    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      assertThat(didConsume).isFalse()
      assertThat(remaining).isZero()
    }
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()
  }

  @Test
  fun `withToken respects limits`() {
    var counter = 0
    repeat((TestRateLimitConfig.capacity * 2).toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    // Should have consumed capacity tokens, then been rejected for capacity tokens
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(exceptionMetrics.count()).isZero()
    // We should have been able to increment the counter until we consumed the bucket
    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)
  }

  @Test
  fun `bucket is refilled on schedule`() {
    var counter = 0
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isZero()
    assertThat(exceptionMetrics.count()).isZero()

    val result = rateLimiter.withToken(KEY, TestRateLimitConfig) {
      counter++
    }
    assertThat(result.consumptionData.didConsume).isFalse()
    assertThat(result.result).isNull()
    assertThat(result.consumptionData.remaining).isZero()

    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    assertThat(consumedMetrics.count()).isEqualTo(TestRateLimitConfig.capacity.toDouble())
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()

    // Elapse enough time that the next request refills the bucket
    fakeClock.add(TestRateLimitConfig.refillPeriod)
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    assertThat(counter)
      .isEqualTo(10)
      .isEqualTo(TestRateLimitConfig.capacity * 2)

    assertThat(consumedMetrics.count()).isEqualTo(10.0)
    assertThat(rejectedMetrics.count()).isOne()
    assertThat(exceptionMetrics.count()).isZero()
  }

  @Test
  fun `exception metrics are tracked`() {
    var counter = 0
    rateLimiter.withToken(KEY, TestRateLimitConfig) {
      counter++
    }
    assertThat(counter).isOne()
    assertThat(consumedMetrics.count()).isOne()
    assertThat(rejectedMetrics.count()).isZero()
    assertThat(exceptionMetrics.count()).isZero()

    setException(RuntimeException())
    assertThrows<RuntimeException> {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
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
