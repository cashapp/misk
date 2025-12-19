package com.squareup.exemplar

import com.squareup.exemplar.actions.ExampleRateLimitConfiguration
import com.squareup.exemplar.actions.RateLimitedAction
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Inject
import misk.exceptions.TooManyRequestsException
import misk.time.FakeClock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import wisp.ratelimiting.RateLimiterMetrics

abstract class AbstractRateLimitedActionTests {
  @Inject private lateinit var fakeClock: FakeClock

  @Inject private lateinit var metricRegistry: MeterRegistry

  @Inject private lateinit var rateLimitedAction: RateLimitedAction

  private val metrics by lazy { RateLimiterMetrics(metricRegistry) }

  private val consumedMetrics by lazy {
    metrics.consumptionAttempts(ExampleRateLimitConfiguration, RateLimiterMetrics.ConsumptionResult.SUCCESS)
  }

  private val rejectedMetrics by lazy {
    metrics.consumptionAttempts(ExampleRateLimitConfiguration, RateLimiterMetrics.ConsumptionResult.REJECTED)
  }

  private val exceptionMetrics by lazy {
    metrics.consumptionAttempts(ExampleRateLimitConfiguration, RateLimiterMetrics.ConsumptionResult.EXCEPTION)
  }

  abstract fun setException()

  @Test
  fun `should throw when we reach limit`() {
    repeat(ExampleRateLimitConfiguration.capacity.toInt()) {
      val response = assertDoesNotThrow { rateLimitedAction.rateLimitedExample() }
      Assertions.assertThat(response.number).isNotNull()
      Assertions.assertThat(consumedMetrics.count()).isEqualTo(it.toDouble() + 1.0)
      Assertions.assertThat(rejectedMetrics.count()).isZero()
      Assertions.assertThat(exceptionMetrics.count()).isZero()
    }
    assertThrows<TooManyRequestsException> { rateLimitedAction.rateLimitedExample() }
    Assertions.assertThat(consumedMetrics.count()).isEqualTo(ExampleRateLimitConfiguration.capacity.toDouble())
    Assertions.assertThat(rejectedMetrics.count()).isOne()
    Assertions.assertThat(exceptionMetrics.count()).isZero()

    fakeClock.add(ExampleRateLimitConfiguration.refillPeriod)

    val response = assertDoesNotThrow { rateLimitedAction.rateLimitedExample() }
    Assertions.assertThat(consumedMetrics.count()).isEqualTo(ExampleRateLimitConfiguration.capacity.toDouble() + 1.0)
    Assertions.assertThat(rejectedMetrics.count()).isOne()
    Assertions.assertThat(exceptionMetrics.count()).isZero()

    Assertions.assertThat(response.number).isNotNull()
  }

  @Test
  fun `exception metrics are tracked`() {
    rateLimitedAction.rateLimitedExample()
    // Force exception
    setException()
    assertThrows<Throwable> { rateLimitedAction.rateLimitedExample() }
    Assertions.assertThat(consumedMetrics.count()).isOne()
    Assertions.assertThat(rejectedMetrics.count()).isZero()
    Assertions.assertThat(exceptionMetrics.count()).isOne()
  }
}
