package wisp.ratelimiting

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class RateLimiterMetrics(private val meterRegistry: MeterRegistry) {
  fun consumptionAttempts(
    configuration: RateLimitConfiguration,
    consumptionResult: ConsumptionResult
  ): Counter = Counter.builder(ATTEMPT_COUNTER_NAME)
    .description("count of successful rate limiter consumption attempts by config and status")
    .tag(RATE_LIMIT_TAG, configuration.name)
    .tag(RESULT_TAG, consumptionResult.name)
    .register(meterRegistry)

  fun tokensConsumed(configuration: RateLimitConfiguration): Counter =
    Counter.builder(TOTAL_CONSUMED_COUNTER_NAME)
      .description("count of total tokens consumed by config")
      .tag(RATE_LIMIT_TAG, configuration.name)
      .register(meterRegistry)

  enum class ConsumptionResult {
    /**
     * A token was consumed successfully
     */
    SUCCESS,

    /**
     * There were insufficient tokens in the bucket
     */
    REJECTED,

    /**
     * An exception was thrown while attempting to consume a token
     */
    EXCEPTION
  }

  companion object {
    const val ATTEMPT_COUNTER_NAME = "rate_limiter.consumption_attempts"
    const val TOTAL_CONSUMED_COUNTER_NAME = "rate_limiter.tokens_consumed"
    const val RATE_LIMIT_TAG = "rate_limit_config"
    const val RESULT_TAG = "result"
  }
}
