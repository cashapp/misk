package misk.redis

import com.google.inject.Guice.createInjector
import com.google.inject.ProvisionException
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.deployment.PRODUCTION
import wisp.deployment.TESTING
import jakarta.inject.Inject
import redis.clients.jedis.ConnectionPoolConfig

@MiskTest
class RedisAuthPasswordEnvTest {
  @Test fun `injection succeeds with password-less config in fake environments`() {
    assertThat(DockerRedis.replicationGroupConfig.redis_auth_password).isEmpty()
    val injector = createInjector(fakeEnv, realRedisModule)
    val redis = injector.getInstance(keyOf<RedisConsumer>()).redis
    assertThat(redis).isInstanceOf(RealRedis::class.java)
    redis["hello"] = "world".encodeUtf8()
    assertThat(redis["hello"]?.utf8()).isEqualTo("world")
  }

  @Test fun `injection fails with password-less config in real environments`() {
    assertThat(DockerRedis.replicationGroupConfig.redis_auth_password).isEmpty()
    val injector = createInjector(realEnv, realRedisModule)
    val ex = assertThrows<ProvisionException> { injector.getInstance(keyOf<RedisConsumer>()) }
    assertThat(ex).hasCauseInstanceOf(IllegalStateException::class.java)
    assertThat(ex.cause)
      .hasMessage("This Redis client is configured to require an auth password, but none was provided!")
  }

  private class RedisConsumer @Inject constructor(val redis: Redis)

  private val fakeEnv = DeploymentModule(TESTING)

  private val realEnv = DeploymentModule(PRODUCTION)

  private val realRedisModule = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
    }
  }
}
