package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.redis.testing.DockerRedisCluster
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.UnifiedJedis
import wisp.deployment.TESTING

/**
 * Provides test coverage/parity for pipelined operations on a Redis cluster.
 */
@MiskTest(startService = true)
class PipelinedRedisClusterTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisClusterModule(DockerRedisCluster.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))

      val jedisProvider = getProvider(UnifiedJedis::class.java)
      bind<Redis>().annotatedWith<AlwaysPipelined>().toProvider {
        TestAlwaysPipelinedRedis(jedisProvider.get())
      }.asSingleton()
    }
  }

  @Inject @AlwaysPipelined override lateinit var redis: Redis
}
