package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedisCluster
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.exceptions.JedisClusterOperationException
import wisp.deployment.TESTING
import kotlin.test.assertEquals
import kotlin.test.assertNull

@MiskTest
class RealRedisClusterTest : AbstractRedisClusterTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisClusterModule(DockerRedisCluster.config, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
    }
  }

  @Suppress("unused")
  @MiskExternalDependency
  private val dockerRedisCluster = DockerRedisCluster

  @Inject override lateinit var redis: Redis
}
