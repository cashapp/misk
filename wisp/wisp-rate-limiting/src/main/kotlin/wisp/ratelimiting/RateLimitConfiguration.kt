package wisp.ratelimiting

import java.time.Duration

/**
 * The rate limit configuration applied to a rate limit bucket
 */
interface RateLimitConfiguration {
  /**
   * The maximum number of tokens that can accumulate in the bucket
   */
  val capacity: Long

  /**
   * A name identifying the configuration, e.g. "OriginationFileProcessing" for a rate limit
   * configuration governing the rate at which origination files can be processed
   */
  val name: String
  /**
   * The amount of tokens added back to the limit bucket every [refillPeriod]
   */
  val refillAmount: Long
  /**
   * The period of time over which [refillAmount] tokens are added back to the bucket
   */
  val refillPeriod: Duration
}
