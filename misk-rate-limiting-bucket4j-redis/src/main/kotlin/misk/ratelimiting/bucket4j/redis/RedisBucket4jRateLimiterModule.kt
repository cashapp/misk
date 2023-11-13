package misk.ratelimiting.bucket4j.redis

import com.google.common.base.Ticker
import com.google.inject.Provides
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.serialization.Mapper
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import misk.redis.JedisPoolWithMetrics
import misk.redis.RedisClientMetrics
import misk.redis.RedisConfig
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.Deployment
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
  private val redisConfig: RedisConfig,
  private val jedisPoolConfig: JedisPoolConfig,
  private val additionalTtl: Duration = Duration.ofSeconds(5),
  private val useSsl: Boolean = true
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Clock>()
    requireBinding<MeterRegistry>()
    install(ServiceModule<JedisPoolService>().enhancedBy<ReadyService>())
  }

  @Provides @Singleton
  fun providedRateLimiter(
    clock: Clock,
    jedisPool: JedisPool,
    metricsRegistry: MeterRegistry
  ): RateLimiter {
    val proxyManager = JedisBasedProxyManager.builderFor(jedisPool)
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

  @Provides @Singleton
  fun provideJedisPool(
    deployment: Deployment,
    metrics: Metrics,
    ticker: Ticker,
  ): JedisPool {
    // Get the first replication group, we only support 1 replication group per service.
    val replicationGroup = redisConfig[redisConfig.keys.first()]
      ?: throw RuntimeException("At least 1 replication group must be specified")
    val clientMetrics = RedisClientMetrics(ticker, metrics)
    return JedisPoolWithMetrics(
      metrics = clientMetrics,
      poolConfig = jedisPoolConfig,
      replicationGroupConfig = replicationGroup,
      ssl = useSsl,
      requiresPassword = deployment.isReal
    )
  }
}
