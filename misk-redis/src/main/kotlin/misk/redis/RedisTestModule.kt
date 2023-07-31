package misk.redis

import misk.inject.KAbstractModule
import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier
import kotlin.random.Random

@Deprecated("Moved to misk-redis-testing.", ReplaceWith("misk.redis.testing.RedisTestModule"))
class RedisTestModule @JvmOverloads constructor(private val random: Random = Random.Default) :
  KAbstractModule() {
  override fun configure() {
    bind<Random>().annotatedWith<ForFakeRedis>().toInstance(random)
    bind<Redis>().toInstance(FakeRedis())
  }
}

@Qualifier
@BindingAnnotation
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
@Deprecated("Moved to misk-redis-testing.", ReplaceWith("misk.redis.testing.ForFakeRedis"))
annotation class ForFakeRedis
