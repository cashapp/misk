package misk.redis.testing

import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.redis.Redis
import misk.time.FakeClock
import javax.inject.Qualifier
import kotlin.random.Random

@Deprecated("Replace the dependency on misk-redis-testing with testFixtures(misk-redis)")
class RedisTestModule(private val random: Random = Random.Default) : KAbstractModule() {
  override fun configure() {
    requireBinding<FakeClock>()
    bind(keyOf<Random>(ForFakeRedis::class)).toInstance(random)
    bind<Redis>().to(keyOf<FakeRedis>())
  }
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class ForFakeRedis
