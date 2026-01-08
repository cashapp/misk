package wisp.ratelimiting

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

class RateLimiterMetrics(private val meterRegistry: MeterRegistry) {
  fun consumptionAttempts(configuration: RateLimitConfiguration, consumptionResult: ConsumptionResult): Counter =
    Counter.builder(ATTEMPT_COUNTER_NAME)
      .description("count of successful rate limiter consumption attempts by config and status")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .tag(RESULT_TAG, consumptionResult.name)
      .register(meterRegistry)

  fun tokensConsumed(configuration: RateLimitConfiguration): Counter =
    Counter.builder(TOTAL_CONSUMED_COUNTER_NAME)
      .description("count of total tokens consumed by config")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .register(meterRegistry)

  fun limitConsumptionDuration(configuration: RateLimitConfiguration): DistributionSummary =
    DistributionSummary.builder(LIMIT_CONSUMPTION_DURATION)
      .description("duration in ms of rate limit consumption")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .register(meterRegistry)

  fun limitTestDuration(configuration: RateLimitConfiguration): DistributionSummary =
    DistributionSummary.builder(LIMIT_TEST_DURATION)
      .description("duration in ms of rate limit consumption testing")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .register(meterRegistry)

  fun limitAvailabilityDuration(configuration: RateLimitConfiguration): DistributionSummary =
    DistributionSummary.builder(LIMIT_AVAILABILITY_DURATION)
      .description("duration in ms of rate limit token availability checking")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .register(meterRegistry)

  fun limitReleaseDuration(configuration: RateLimitConfiguration): DistributionSummary =
    DistributionSummary.builder(LIMIT_RELEASE_DURATION)
      .description("duration in ms of rate limit release")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .register(meterRegistry)

  fun limitResetDuration(configuration: RateLimitConfiguration): DistributionSummary =
    DistributionSummary.builder(LIMIT_RESET_DURATION)
      .description("duration in ms of rate limit bucket reset")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .register(meterRegistry)

  enum class ConsumptionResult {
    /** A token was consumed successfully */
    SUCCESS,

    /** There were insufficient tokens in the bucket */
    REJECTED,

    /** An exception was thrown while attempting to consume a token */
    EXCEPTION,
  }

  companion object {
    const val LIMIT_CONSUMPTION_DURATION = "rate_limiter.limit_consumption_duration"
    const val LIMIT_TEST_DURATION = "rate_limiter.limit_test_duration"
    const val LIMIT_AVAILABILITY_DURATION = "rate_limiter.limit_availability_duration"
    const val LIMIT_RELEASE_DURATION = "rate_limiter.limit_release_duration"
    const val LIMIT_RESET_DURATION = "rate_limiter.limit_reset_duration"
    const val ATTEMPT_COUNTER_NAME = "rate_limiter.consumption_attempts"
    const val TOTAL_CONSUMED_COUNTER_NAME = "rate_limiter.tokens_consumed"
    const val RATE_LIMIT_TAG = "rate_limit_config"
    const val RESULT_TAG = "result"
  }
}
