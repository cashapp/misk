package misk.redis

import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.JedisPoolConfig
import javax.inject.Inject

@MiskTest
class RealRedisTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  override val module: Module = object: KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.config, JedisPoolConfig(), useSsl = false))
    }
  }

  @Suppress("unused")
  @MiskExternalDependency
  private val dockerRedis = DockerRedis

  @Inject override lateinit var redis: Redis
}
