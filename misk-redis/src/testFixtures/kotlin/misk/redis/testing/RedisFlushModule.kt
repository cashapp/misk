package misk.redis.testing

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule

class RedisFlushModule : KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<RedisFlushService>()
        .enhancedBy<ReadyService>()
    )
  }
}
