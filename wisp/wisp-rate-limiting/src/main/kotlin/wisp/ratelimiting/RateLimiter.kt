package wisp.ratelimiting

import java.time.Instant

/**
 * An interface for acquiring and releasing rate limit tokens
 *
 * Terms:
 * * `key` - a unique identifier for the entity or operation being rate limited
 * * `bucket` - a container for rate limit tokens applied to a specific key
 * * `token` - a unit of rate limit capacity. When we go to perform a rate limited operation, we attempt to consume a
 *   token from the bucket. If successful, we can perform the operation.
 *
 * See also: https://en.wikipedia.org/wiki/Token_bucket
 */
interface RateLimiter {
  /**
   * Consumes [amount] tokens from the bucket associated with the given key This will raise any exception thrown by the
   * bucket4j proxy manager implementation, e.g. subclasses of [JedisException] when using the Jedis implementation.
   */
  fun consumeToken(key: String, configuration: RateLimitConfiguration, amount: Long = 1): ConsumptionData

  /**
   * This tests whether [amount] tokens are available in the bucket associated with the given key. It is essentially a
   * dry run of [consumeToken]. Note that this data may be stale when it comes back, as time has elapsed and other pods
   * could have taken tokens in the meantime.
   */
  fun testConsumptionAttempt(
    key: String,
    configuration: RateLimitConfiguration,
    amount: Long = 1,
  ): TestConsumptionResult

  /**
   * Releases [amount] tokens back to the bucket associated with the given key This will raise any exception thrown by
   * the bucket4j proxy manager implementation, e.g. subclasses of [JedisException] when using the Jedis implementation.
   */
  fun releaseToken(key: String, configuration: RateLimitConfiguration, amount: Long = 1)

  /**
   * Returns how many tokens remain in the bucket. Note that this data may be stale when it comes back, as time has
   * elapsed and other pods could have taken tokens in the meantime.
   */
  fun availableTokens(key: String, configuration: RateLimitConfiguration): Long

  /** Resets the bucket back to its maximum capacity */
  fun resetBucket(key: String, configuration: RateLimitConfiguration)

  /**
   * Executes the given function if a token is available This will raise any exception thrown by the bucket4j proxy
   * manager implementation, e.g. subclasses of [JedisException] when using the Jedis implementation.
   */
  fun <T> withToken(key: String, configuration: RateLimitConfiguration, f: () -> T): ExecutionResult<T> {
    val consumptionData = consumeToken(key, configuration)
    return if (consumptionData.didConsume) {
      ExecutionResult(f(), consumptionData)
    } else {
      ExecutionResult(null, consumptionData)
    }
  }

  data class ConsumptionData(
    /** Whether a token was consumed */
    val didConsume: Boolean,
    /** Count of tokens remaining in the bucket */
    val remaining: Long,
    /** The time at which the bucket will be reset. */
    val resetTime: Instant,
  )

  data class TestConsumptionResult(
    /** Whether a token could have been consumed */
    val couldHaveConsumed: Boolean,
    /**
     * Count of tokens remaining in the bucket Note - this is the actual amount remaining, not the amount that would be
     * remaining if the test consumption had been a real consumption
     */
    val remaining: Long,
    /** The time at which the bucket will be reset. */
    val resetTime: Instant,
  )

  data class ExecutionResult<T>(
    /** The result of the execution if a token was consumed, `null` otherwise */
    val result: T?,
    /** `true` if a token was consumed, `false` otherwise */
    val consumptionData: ConsumptionData,
  )
}
