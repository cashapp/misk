package wisp.ratelimiting.bucket4j

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import wisp.ratelimiting.RateLimitConfiguration
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.RateLimiterMetrics
import wisp.ratelimiting.RateLimiterMetrics.ConsumptionResult
import java.time.Clock

class Bucket4jRateLimiter(
  private val bucketProxy: ProxyManager<String>,
  private val clock: Clock,
  meterRegistry: MeterRegistry = Metrics.globalRegistry
) : RateLimiter {
  private val metrics = RateLimiterMetrics(meterRegistry)

  override fun consumeToken(
    key: String,
    configuration: RateLimitConfiguration,
    amount: Long
  ): RateLimiter.ConsumptionData {
    val result = try {
      val bucket = getBucketProxy(key, configuration)
      bucket.tryConsumeAndReturnRemaining(amount)
    } catch (e: Exception) {
      metrics.consumptionAttempts(configuration, ConsumptionResult.EXCEPTION).increment()
      throw e
    }
    val metricResult = if (result.isConsumed) {
      ConsumptionResult.SUCCESS
    } else {
      ConsumptionResult.REJECTED
    }
    metrics.consumptionAttempts(configuration, metricResult).increment()
    if (metricResult == ConsumptionResult.SUCCESS) {
      metrics.tokensConsumed(configuration).increment(amount.toDouble())
    }
    return RateLimiter.ConsumptionData(
      didConsume = result.isConsumed,
      remaining = result.remainingTokens,
      resetTime = clock.instant().plusNanos(result.nanosToWaitForReset)
    )
  }

  override fun releaseToken(key: String, configuration: RateLimitConfiguration, amount: Long) {
    val bucket = getBucketProxy(key, configuration)
    bucket.addTokens(amount)
  }

  private fun getBucketProxy(key: String, configuration: RateLimitConfiguration): BucketProxy {
    val bucketConfig = BucketConfiguration.builder()
      .addLimit(configuration.toBandwidth())
      .build()

    return bucketProxy.builder().build(key, bucketConfig)
  }

  private fun RateLimitConfiguration.toBandwidth(): Bandwidth {
    return Bandwidth.builder()
      .capacity(capacity)
      .refillIntervally(refillAmount, refillPeriod)
      .initialTokens(capacity)
      .build()
  }
}
