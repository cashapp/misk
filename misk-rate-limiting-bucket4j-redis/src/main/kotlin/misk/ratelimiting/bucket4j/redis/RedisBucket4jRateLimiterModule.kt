package misk.ratelimiting.bucket4j.redis

import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Provider
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.serialization.Mapper
import io.github.bucket4j.redis.jedis.Bucket4jJedis
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.time.Duration
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import redis.clients.jedis.UnifiedJedis
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.bucket4j.Bucket4jRateLimiter
import wisp.ratelimiting.bucket4j.ClockTimeMeter

/**
 * Configures a [RateLimiter] that uses Redis as a backend.
 *
 * @param additionalTtl Additional duration to add to the base TTL of each rate limit bucket, which is the duration of
 *   the refill period. This is a performance optimization that enables bucket reuse when a request comes in after the
 *   bucket has been refilled, since reuse is cheaper than creating a new bucket
 * @param keyMapper Optional mapper to transform Redis keys
 */
class RedisBucket4jRateLimiterModule
@JvmOverloads
constructor(
  private val additionalTtl: Duration = Duration.ofSeconds(5),
  private val qualifier: Annotation? = null,
  private val keyMapper: Mapper<String>? = null,
  private val maxRetries: Int = 3,
  private val retryTimeout: Duration = Duration.ofMillis(25),
  private val configMutator: Bucket4jJedis.JedisBasedProxyManagerBuilder<String>.() -> Unit = {},
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Clock>()
    requireBinding<MeterRegistry>()
    val unifiedJedisKey =
      if (qualifier == null) Key.get(UnifiedJedis::class.java) else Key.get(UnifiedJedis::class.java, qualifier)
    requireBinding(unifiedJedisKey)
    val unifiedJedisProvider = binder().getProvider(unifiedJedisKey)

    val rateLimiterKey =
      if (qualifier == null) Key.get(RateLimiter::class.java) else Key.get(RateLimiter::class.java, qualifier)
    bind(rateLimiterKey)
      .toProvider(
        RedisBucket4jRateLimiterProvider(
          additionalTtl = additionalTtl,
          unifiedJedisProvider = unifiedJedisProvider,
          keyMapper = keyMapper ?: Mapper.STRING,
          maxRetries = maxRetries,
          retryTimeout = retryTimeout,
          configMutator = configMutator,
        )
      )
      .asSingleton()
  }

  private class RedisBucket4jRateLimiterProvider(
    val additionalTtl: Duration,
    private val unifiedJedisProvider: Provider<UnifiedJedis>,
    private val keyMapper: Mapper<String>,
    private val maxRetries: Int,
    private val retryTimeout: Duration,
    private val configMutator: Bucket4jJedis.JedisBasedProxyManagerBuilder<String>.() -> Unit,
  ) : Provider<RateLimiter> {
    @Inject lateinit var clock: Clock
    @Inject lateinit var metricsRegistry: MeterRegistry

    override fun get(): RateLimiter {
      val proxyManager =
        Bucket4jJedis.casBasedBuilder(unifiedJedisProvider.get())
          // Use Clock instead of calling System.currentTimeMillis() for refill determination
          // Equivalent logic at runtime, but lets us mock the refill times in integration tests
          .clientClock(ClockTimeMeter(clock))
          .keyMapper(keyMapper)
          .maxRetries(maxRetries)
          .requestTimeout(retryTimeout)
          .expirationAfterWrite(
            // Set Redis TTLs to the bucket refill period + additionalTtl
            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(additionalTtl)
          )
          .apply { configMutator() }
          .build()
      return Bucket4jRateLimiter(proxyManager, clock, metricsRegistry)
    }
  }
}
