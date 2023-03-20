package misk.redis

import com.google.inject.Guice.createInjector
import com.google.inject.ProvisionException
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.redis.testing.DockerRedis
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.PRODUCTION
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest
class RedisAuthPasswordEnvTest {
  @Suppress("unused")
  @MiskExternalDependency
  private val dockerRedis = DockerRedis // Start Redis so that we can properly provision a client.

  @Test fun `injection succeeds with password-less config in fake environments`() {
    assertThat(DockerRedis.config.values.first().redis_auth_password).isEmpty()
    val injector = createInjector(fakeEnv, realRedisModule)
    val redis = injector.getInstance(keyOf<RedisConsumer>()).redis
    assertThat(redis).isInstanceOf(RealRedis::class.java)
    redis["hello"] = "world".encodeUtf8()
    assertThat(redis["hello"]?.utf8()).isEqualTo("world")
  }

  @Test fun `injection fails with password-less config in real environments`() {
    assertThat(DockerRedis.config.values.first().redis_auth_password).isEmpty()
    val injector = createInjector(realEnv, realRedisModule)
    val ex = assertThrows<ProvisionException> { injector.getInstance(keyOf<RedisConsumer>()) }
    assertThat(ex).hasCauseInstanceOf(IllegalStateException::class.java)
    assertThat(ex.cause).hasMessage("Redis auth password cannot be empty in a real environment!")
  }

  private class RedisConsumer @Inject constructor(val redis: Redis)

  private val fakeEnv = DeploymentModule(TESTING)

  private val realEnv = DeploymentModule(PRODUCTION)

  private val realRedisModule = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.config, JedisPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
    }
  }
}
