package misk.redis.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import jakarta.inject.Singleton
import misk.redis.Redis
import wisp.logging.getLogger

/**
 * Flushes all Redis databases on startup.
 *
 * Note: If your test does not use `@MiskTest(startService = true)`,
 * you will need instead to manually flush redis via `@BeforeEach`. See examples in `RealRedisTest`
 */
@Singleton
class RedisFlushService @Inject constructor() : AbstractIdleService() {
  @Inject(optional = true) private lateinit var redis: Redis
  override fun startUp() {
    logger.info("Flushing Redis")
    redis.flushAll()
    logger.info("Flushed Redis")
  }

  override fun shutDown() {}

  companion object {
    private val logger = getLogger<RedisFlushService>()
  }
}
