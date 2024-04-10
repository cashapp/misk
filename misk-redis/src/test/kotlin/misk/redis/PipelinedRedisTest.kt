package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.UnifiedJedis
import wisp.deployment.TESTING
import javax.inject.Provider

/**
 * Provides test coverage/parity for pipelined operations on a connection-pooled Redis client.
 */
@MiskTest
class PipelinedRedisTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.config, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))

      val jedisProvider = getProvider(UnifiedJedis::class.java)
      bind<Redis>().annotatedWith<AlwaysPipelined>().toProvider(Provider {
        TestAlwaysPipelinedRedis(jedisProvider.get())
      }).asSingleton()
    }
  }

  @Inject @AlwaysPipelined override lateinit var redis: Redis
}
