package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import redis.clients.jedis.ConnectionPoolConfig
import wisp.deployment.TESTING

@MiskTest
class RealRedisTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.config, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
    }
  }

  @Suppress("unused")
  @MiskExternalDependency
  private val dockerRedis = DockerRedis

  @Inject override lateinit var redis: Redis

  @Test
  fun `watch and unwatch succeeds`() {
    redis.watch("key1")
    redis.watch("key2")

    redis.set("key1", "value1".encodeUtf8())

    redis.unwatch("key2")
  }

  @Test
  fun `transaction is completed if watched key is not modified before the EXEC command`() {
    redis.watch("key3")
    redis.multi().use { txn ->
      redis.get("key3")
      redis.set("key4", "value4".encodeUtf8())
      txn.set("key3", "value3")
      txn.exec()
    }

    assertThat(redis.get("key3")).isEqualTo("value3".encodeUtf8())
    assertThat(redis.get("key4")).isEqualTo("value4".encodeUtf8())
  }

  @Test
  fun `transaction is aborted if at least one watched key is modified before the EXEC command`() {
    redis.watch("key5", "key6")
    redis.multi().use { txn ->
      redis["key5"] = "value5".encodeUtf8()
      txn.set("key6", "value6")
      txn.exec()
    }

    assertThat(redis["key5"]).isEqualTo("value5".encodeUtf8())
    assertThat(redis["key6"]).isNull()
  }

}
