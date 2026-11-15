package misk.redis.testing

import jakarta.inject.Qualifier
import kotlin.random.Random
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.redis.Redis
import misk.testing.TestFixture
import misk.time.FakeClock

/** Installs a singleton [FakeRedis] for testing. */
class RedisTestModule(private val random: Random = Random.Default) : KAbstractModule() {
  override fun configure() {
    requireBinding<FakeClock>()
    bind(keyOf<Random>(ForFakeRedis::class)).toInstance(random)
    bind<Redis>().to(keyOf<FakeRedis>()).asEagerSingleton()
    bind<FakeRedis>().asEagerSingleton()
    multibind<TestFixture>().to<FakeRedis>()
  }
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class ForFakeRedis
