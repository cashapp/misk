package misk.redis.testing

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import misk.logging.getLogger
import misk.redis.Redis
import misk.testing.TestFixture

/**
 * Flushes all Redis databases on startup.
 *
 * Note: If your test does not use `@MiskTest(startService = true)`,
 * you will need instead to manually flush redis via `@BeforeEach`. See examples in `RealRedisTest`
 */
@Singleton
class RedisFlushService @Inject constructor() : AbstractIdleService(), TestFixture {
  @Inject private lateinit var redisProvider: Provider<Redis>
  private val redis by lazy { redisProvider.get() }

  override fun startUp() {
    flush()
  }

  override fun shutDown() {}

  override fun reset() {
    flush()
  }

  private fun flush() {
    logger.info("Flushing Redis")
    redis.flushDB()
  }

  companion object {
    private val logger = getLogger<RedisFlushService>()
  }
}
