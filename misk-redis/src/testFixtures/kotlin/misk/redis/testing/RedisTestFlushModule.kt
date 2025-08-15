package misk.redis.testing

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.testing.TestFixture

/**
 * Installs a service that flushes all Redis keys in all databases on startup.
 * Intended to ease cleanup in tests that use `@MiskTest(startService = true)`.
 */
class RedisTestFlushModule : KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<RedisFlushService>()
        .dependsOn<ReadyService>()
    )
    multibind<TestFixture>().to<RedisFlushService>()
  }
}
