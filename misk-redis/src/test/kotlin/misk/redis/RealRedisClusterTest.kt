package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedisCluster
import misk.redis.testing.RedisTestFlushModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.ConnectionPoolConfig
import wisp.deployment.TESTING

@MiskTest(startService = true)
class RealRedisClusterTest : AbstractRedisClusterTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(RedisClusterModule(DockerRedisCluster.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
        install(RedisTestFlushModule())
      }
    }

  @Inject override lateinit var redis: Redis
}
