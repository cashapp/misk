package misk.redis

import misk.inject.KAbstractModule

internal class RedisTestModule : KAbstractModule() {
  override fun configure() {
    bind<Redis>().toInstance(FakeRedis())
  }
}
