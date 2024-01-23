package misk.ratelimiting.bucket4j.redis

import com.google.inject.Provides
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.serialization.Mapper
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import redis.clients.jedis.UnifiedJedis
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.bucket4j.Bucket4jRateLimiter
import wisp.ratelimiting.bucket4j.ClockTimeMeter
import java.time.Clock
import java.time.Duration

/**
 * Configures a [RateLimiter] that uses Redis as a backend.
 * @param additionalTtl Additional duration to add to the base TTL of each rate limit bucket,
 * which is the duration of the refill period. This is a performance optimization that
 * enables bucket reuse when a request comes in after the bucket has been refilled,
 * since reuse is cheaper than creating a new bucket
 */
class RedisBucket4jRateLimiterModule @JvmOverloads constructor(
  private val additionalTtl: Duration = Duration.ofSeconds(5),
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Clock>()
    requireBinding<MeterRegistry>()
    requireBinding<UnifiedJedis>()
  }

  @Provides @Singleton
  fun providedRateLimiter(
    clock: Clock,
    metricsRegistry: MeterRegistry,
    unifiedJedis: UnifiedJedis
  ): RateLimiter {
    val proxyManager = JedisBasedProxyManager.builderFor(unifiedJedis)
      .withClientSideConfig(
        // Use Clock instead of calling System.currentTimeMillis() for refill determination
        // Equivalent logic at runtime, but lets us mock the refill times in integration tests
        ClientSideConfig.getDefault().withClientClock(ClockTimeMeter(clock))
      )
      .withExpirationStrategy(
        // Set Redis TTLs to the bucket refill period + additionalTtl
        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(additionalTtl)
      )
      .withKeyMapper(Mapper.STRING)
      .build()
    return Bucket4jRateLimiter(proxyManager, clock, metricsRegistry)
  }
}
