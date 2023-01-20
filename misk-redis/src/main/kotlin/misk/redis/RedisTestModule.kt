package misk.redis

import misk.inject.KAbstractModule
import javax.inject.Qualifier
import kotlin.random.Random

class RedisTestModule(private val random: Random = Random.Default) : KAbstractModule() {
  override fun configure() {
    bind<Random>().annotatedWith<ForFakeRedis>().toInstance(random)
    bind<Redis>().toInstance(FakeRedis())
  }
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ForFakeRedis
