package misk.redis.testing

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule

/**
 * Installs a service that flushes all Redis keys in all databases on startup.
 * Intended to ease cleanup in tests that use `@MiskTest(startService = true)`.
 */
class RedisTestFlushModule : KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<RedisFlushService>()
        .enhancedBy<ReadyService>()
    )
  }
}
