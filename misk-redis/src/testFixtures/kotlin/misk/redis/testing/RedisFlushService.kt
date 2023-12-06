package misk.redis.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import jakarta.inject.Singleton
import misk.redis.Redis
import redis.clients.jedis.JedisPool
import wisp.logging.getLogger

/**
 * Flushes all Redis databases on startup.
 *
 * Note: If your test does not use `@MiskTest(startService = true)`,
 * you will need instead to manually flush redis via `@BeforeEach`. See examples in `RealRedisTest`
 */
@Singleton
class RedisFlushService @Inject constructor() : AbstractIdleService() {
  // TODO(tgregory) Remove this once bucket4j redis uses UnifiedJedis
  @Inject(optional = true) private lateinit var jedisPool: JedisPool
  @Inject(optional = true) private lateinit var redis: Redis
  override fun startUp() {
    logger.info("Flushing Redis")
    if (this::redis.isInitialized) {
      redis.flushAll()
    }
    if (this::jedisPool.isInitialized) {
      jedisPool.resource.use { jedis -> jedis?.flushAll() }
    }
    logger.info("Flushed Redis")
  }

  override fun shutDown() {}

  companion object {
    private val logger = getLogger<RedisFlushService>()
  }
}
