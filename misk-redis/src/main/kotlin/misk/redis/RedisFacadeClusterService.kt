package misk.redis

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.logging.getLogger
import redis.clients.jedis.UnifiedJedis

/** Provides a [Redis] built from a [UnifiedJedis] provided by [RedisJedisClusterService] */
@Singleton
internal class RedisFacadeClusterService
@Inject
constructor(private val clientMetrics: RedisClientMetrics, private val unifiedJedisProvider: Provider<UnifiedJedis>) :
  AbstractIdleService(), Provider<Redis> {
  private lateinit var redis: Redis

  override fun startUp() {
    check(!::redis.isInitialized) { "JedisCluster is already initialized. Services must be started only once." }
    logger.info { "Starting ${this::class.simpleName} service" }

    redis = RealRedis(unifiedJedisProvider.get(), clientMetrics)
  }

  override fun shutDown() {
    logger.info { "Stopping ${this::class.simpleName} service" }
    // This is a facade, the RedisJedisClusterService service will close the real client
  }

  override fun get(): Redis {
    check(::redis.isInitialized) {
      "Redis is not connected; were Misk services started? " +
        "If this was a test, try setting @MiskTest(startService = true) on the test class."
    }
    return redis
  }

  companion object {
    private val logger = getLogger<RedisFacadeClusterService>()
  }
}
