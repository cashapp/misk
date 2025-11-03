package wisp.ratelimiting.testing

import wisp.ratelimiting.RateLimitBucketRefillStrategy
import wisp.ratelimiting.RateLimitConfiguration
import java.time.Duration

object TestRateLimitConfig : RateLimitConfiguration {
  private const val BUCKET_CAPACITY = 5L
  private val REFILL_DURATION: Duration = Duration.ofSeconds(30L)

  override val capacity = BUCKET_CAPACITY
  override val name = "test_configuration"
  override val refillAmount = BUCKET_CAPACITY
  override val refillPeriod: Duration = REFILL_DURATION
}

object TestRateLimitConfigRefillGreedily : RateLimitConfiguration {
  private const val BUCKET_CAPACITY = 5L
  private val REFILL_DURATION: Duration = Duration.ofSeconds(30L)

  override val capacity = BUCKET_CAPACITY
  override val name = "test_configuration_refill_greedily"
  override val refillAmount = BUCKET_CAPACITY
  override val refillPeriod: Duration = REFILL_DURATION
  override val refillStrategy: RateLimitBucketRefillStrategy = RateLimitBucketRefillStrategy.GREEDY
}
