package wisp.ratelimiting

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class RateLimiterMetrics(private val meterRegistry: MeterRegistry) {
  fun consumptionCount(
    configuration: RateLimitConfiguration,
    consumptionResult: ConsumptionResult
  ): Counter = Counter.builder(COUNTER_NAME)
    .description("count of successful rate limiter consumptions")
    .tag(RATE_LIMIT_TAG, configuration.name)
    .tag(RESULT_TAG, consumptionResult.name)
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
    const val COUNTER_NAME = "rate_limiter.consumption"
    const val RATE_LIMIT_TAG = "rate_limit_config"
    const val RESULT_TAG = "result"
  }
}
