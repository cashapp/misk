package misk.redis

import misk.inject.KAbstractModule

class RedisTestModule : KAbstractModule() {
  override fun configure() {
    bind<Redis>().toInstance(FakeRedis())
  }
}
