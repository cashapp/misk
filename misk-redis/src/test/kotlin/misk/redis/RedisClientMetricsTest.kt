package misk.redis

import io.prometheus.client.CollectorRegistry
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.RedisClientMetrics.Companion.ACTIVE_CONNECTIONS
import misk.redis.RedisClientMetrics.Companion.DESTROYED_CONNECTIONS_TOTAL
import misk.redis.RedisClientMetrics.Companion.IDLE_CONNECTIONS
import misk.redis.RedisClientMetrics.Companion.MAX_IDLE_CONNECTIONS
import misk.redis.RedisClientMetrics.Companion.MAX_TOTAL_CONNECTIONS
import misk.redis.RedisClientMetrics.Companion.OPERATION_TIME
import misk.redis.testing.DockerRedis
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.TESTING
import javax.inject.Inject

@Suppress("UsePropertyAccessSyntax")
@MiskTest
class RedisClientMetricsTest {
  @Suppress("unused")
  @MiskExternalDependency private val dockerRedis = DockerRedis

  @Suppress("unused")
  @MiskTestModule private val module = object : KAbstractModule() {
    override fun configure() {
      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())
      install(RedisModule(DockerRedis.config, JedisPoolConfig(), useSsl = false))
    }
  }

  @Inject private lateinit var collectorRegistry: CollectorRegistry
  @Inject private lateinit var redis: Redis

  @Test fun `connections are counted`() {
    // No connections are created or used yet.
    assertThat(collectorRegistry[ACTIVE_CONNECTIONS]).isEqualTo(0.0)
    assertThat(collectorRegistry[IDLE_CONNECTIONS]).isEqualTo(0.0)
    assertThat(collectorRegistry[MAX_TOTAL_CONNECTIONS]).isEqualTo(8.0)
    assertThat(collectorRegistry[MAX_IDLE_CONNECTIONS]).isEqualTo(8.0)

    // Take a connection. The connection should be counted as active while it is held.
    // FIXME: Testing active connection count in a single-threaded testing environment is impossible
    //   without proper Transaction or Pipeline support. Presently misk-redis will return a jedis
    //   that yields a transaction to the pool, which will result in wrong metrics.
    assertThat(redis["hello"]).isNull()

    // The connection is returned to idle, once it is used.
    assertThat(collectorRegistry[ACTIVE_CONNECTIONS]).isEqualTo(0.0)
    assertThat(collectorRegistry[IDLE_CONNECTIONS]).isEqualTo(1.0)

    // No connections are destroyed, because this is exceptional unless we close the client.
    assertThat(collectorRegistry[DESTROYED_CONNECTIONS_TOTAL]).isZero()

    // Close the client to destroy all idle connections.
    redis.close()
    assertThat(collectorRegistry[DESTROYED_CONNECTIONS_TOTAL]).isEqualTo(1.0)
  }

  private operator fun CollectorRegistry.get(metric: String): Double? =
    getSampleValue(metric)
}
