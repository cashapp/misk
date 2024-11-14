package wisp.ratelimiting.bucket4j

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.ConsumptionProbe
import io.github.bucket4j.EstimationProbe
import io.github.bucket4j.TokensInheritanceStrategy
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import wisp.ratelimiting.RateLimitConfiguration
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.RateLimiterMetrics
import wisp.ratelimiting.RateLimiterMetrics.ConsumptionResult
import java.time.Clock
import kotlin.system.measureTimeMillis

class Bucket4jRateLimiter @JvmOverloads constructor(
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
      var consumptionProbe: ConsumptionProbe
      val millisTaken = measureTimeMillis {
        val bucket = getBucketProxy(key, configuration)
        consumptionProbe = bucket.tryConsumeAndReturnRemaining(amount)
      }
      metrics.limitConsumptionDuration(configuration).record(millisTaken.toDouble())

      consumptionProbe
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

  override fun testConsumptionAttempt(
    key: String,
    configuration: RateLimitConfiguration,
    amount: Long
  ): RateLimiter.TestConsumptionResult {
    var estimationProbe: EstimationProbe
    val millisTaken = measureTimeMillis {
      val bucket = getBucketProxy(key, configuration)
      estimationProbe = bucket.estimateAbilityToConsume(amount)
    }
    metrics.limitTestDuration(configuration).record(millisTaken.toDouble())

    return RateLimiter.TestConsumptionResult(
      couldHaveConsumed = estimationProbe.canBeConsumed(),
      remaining = estimationProbe.remainingTokens,
      resetTime = clock.instant().plusNanos(estimationProbe.nanosToWaitForRefill)
    )
  }

  override fun releaseToken(key: String, configuration: RateLimitConfiguration, amount: Long) {
    val millisTaken = measureTimeMillis {
      val bucket = getBucketProxy(key, configuration)
      bucket.addTokens(amount)
    }
    metrics.limitReleaseDuration(configuration).record(millisTaken.toDouble())
  }

  override fun availableTokens(key: String, configuration: RateLimitConfiguration): Long {
    var availableTokens: Long
    val millisTaken = measureTimeMillis {
      val bucket = getBucketProxy(key, configuration)
      availableTokens = bucket.availableTokens
    }
    metrics.limitAvailabilityDuration(configuration).record(millisTaken.toDouble())
    return availableTokens
  }

  override fun resetBucket(key: String, configuration: RateLimitConfiguration) {
    val millisTaken = measureTimeMillis {
      val bucket = getBucketProxy(key, configuration)
      bucket.reset()
    }
    metrics.limitResetDuration(configuration).record(millisTaken.toDouble())
  }

  private fun getBucketProxy(key: String, configuration: RateLimitConfiguration): BucketProxy {
    val bucketConfig = BucketConfiguration.builder()
      .addLimit(configuration.toBandwidth())
      .build()

    return bucketProxy.builder().apply {
      configuration.version?.let {
        this.withImplicitConfigurationReplacement(it, TokensInheritanceStrategy.PROPORTIONALLY)
      }
    }.build(key, bucketConfig)
  }

  private fun RateLimitConfiguration.toBandwidth(): Bandwidth {
    return Bandwidth.builder()
      .capacity(capacity)
      .refillIntervally(refillAmount, refillPeriod)
      .initialTokens(capacity)
      .build()
  }
}
