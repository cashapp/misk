package wisp.ratelimiting

import java.time.Duration

/** The rate limit configuration applied to a rate limit bucket */
interface RateLimitConfiguration {
  /** The maximum number of tokens that can accumulate in the bucket */
  val capacity: Long

  /**
   * A name identifying the configuration, e.g. "OriginationFileProcessing" for a rate limit configuration governing the
   * rate at which origination files can be processed
   */
  val name: String

  /** The amount of tokens added back to the limit bucket every [refillPeriod] */
  val refillAmount: Long

  /** The period of time over which [refillAmount] tokens are added back to the bucket */
  val refillPeriod: Duration

  /**
   * The version of the configuration. This allows implicit configuration replacement. Make sure to increase the version
   * when changing the configuration. Desired version should start from 1
   */
  val version: Long?
    get() = null // returns null to be backward compatible

  val refillStrategy: RateLimitBucketRefillStrategy
    get() = RateLimitBucketRefillStrategy.INTERVAL
}
