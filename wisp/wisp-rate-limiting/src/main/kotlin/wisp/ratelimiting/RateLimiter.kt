package wisp.ratelimiting

import java.io.IOException
import java.time.Instant

/**
 * An interface for acquiring and releasing rate limit tokens
 *
 * Terms:
 * * `key` - a unique identifier for the entity or operation being rate limited
 *
 * * `bucket` - a container for rate limit tokens applied to a specific key
 *
 * * `token` - a unit of rate limit capacity. When we go to perform a rate limited operation, we
 * attempt to consume a token from the bucket. If successful, we can perform the operation.
 *
 * See also: https://en.wikipedia.org/wiki/Token_bucket
 */
interface RateLimiter {
  /**
   * Consumes [amount] tokens from the bucket associated with the given key
   * TODO Confirm the exception type is correct
   * @throws IOException if there was an error communicating with rate limiter storage
   */
  fun consumeToken(
    key: String,
    configuration: RateLimitConfiguration,
    amount: Long = 1
  ): ConsumptionData

  /**
   * Releases [amount] tokens back to the bucket associated with the given key
   * @throws IOException if there was an error communicating with rate limiter storage
   */
  fun releaseToken(key: String, configuration: RateLimitConfiguration, amount: Long = 1)

  /**
   * Executes the given function if a token is available
   */
  fun <T> withToken(
    key: String,
    configuration: RateLimitConfiguration,
    f: () -> T
  ): ExecutionResult<T> {
    val consumptionData = consumeToken(key, configuration)
    return if (consumptionData.didConsume) {
      ExecutionResult(f(), consumptionData)
    } else {
      ExecutionResult(null, consumptionData)
    }
  }

  data class ConsumptionData(
    /**
     * Whether a token was consumed
     */
    val didConsume: Boolean,
    /**
     * Count of tokens remaining in the bucket
     */
    val remaining: Long,
    /**
     * The time at which the bucket will be reset.
     */
    val resetTime: Instant
  )

  data class ExecutionResult<T>(
    /**
     * The result of the execution if a token was consumed, `null` otherwise
     */
    val result: T?,
    /**
     * `true` if a token was consumed, `false` otherwise
     */
    val consumptionData: ConsumptionData
  )
}
