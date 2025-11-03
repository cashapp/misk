package misk.redis

import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
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
import misk.redis.testing.RedisTestFlushModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import redis.clients.jedis.ConnectionPoolConfig
import wisp.deployment.TESTING

@MiskTest
class RedisClientMetricsTest {
  @Suppress("unused")
  @MiskTestModule private val module = object : KAbstractModule() {
    override fun configure() {
      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())
      install(RedisModule(DockerRedis.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
      install(RedisTestFlushModule())
    }
  }

  @Inject private lateinit var collectorRegistry: CollectorRegistry
  @Inject private lateinit var redis: Redis

  @Test fun `connections are counted`() {
    // Creating a redis client creates a connection.
    assertThat(collectorRegistry[ACTIVE_CONNECTIONS]).isEqualTo(1.0)
    assertThat(collectorRegistry[IDLE_CONNECTIONS]).isEqualTo(0.0)
    assertThat(collectorRegistry[MAX_TOTAL_CONNECTIONS]).isEqualTo(8.0)
    assertThat(collectorRegistry[MAX_IDLE_CONNECTIONS]).isEqualTo(8.0)

    // Use the connection.
    assertThat(redis["no-value"]).isNull()

    // The connection is returned to idle, once it is used.
    assertThat(collectorRegistry[ACTIVE_CONNECTIONS]).isEqualTo(0.0)
    assertThat(collectorRegistry[IDLE_CONNECTIONS]).isEqualTo(1.0)

    // No connections are destroyed, because this is exceptional unless we close the client.
    assertThat(collectorRegistry[DESTROYED_CONNECTIONS_TOTAL]).isZero()

    // Close the client to destroy all idle connections.
    redis.close()
    assertThat(collectorRegistry[DESTROYED_CONNECTIONS_TOTAL]).isEqualTo(1.0)
  }

  @Test fun `operations are timed`() {
    redis["hello"] = "world".encodeUtf8()
    assertThat(redis["hello"]?.utf8()).isEqualTo("world")

    // No metric for commands that aren't executed.
    assertThat(collectorRegistry.getHistoCount(OPERATION_TIME, listOf("command" to "lolwut")))
      .isNull()

    // A histogram bucket is recorded for the commands that were run.
    assertThat(collectorRegistry.getHistoCount(OPERATION_TIME, listOf("command" to "get")))
      .isOne()

    assertThat(collectorRegistry.getHistoCount(OPERATION_TIME, listOf("command" to "set")))
      .isOne()
  }

  private operator fun CollectorRegistry.get(metric: String): Double? =
    getSampleValue(metric)

  private fun CollectorRegistry.getHistoCount(
    metric: String,
    labels: List<Pair<String, String>>
  ): Double? {
    return getSampleValue(
      "${metric}_count",
      arrayOf(*labels.map { it.first }.toTypedArray()),
      arrayOf(*labels.map { it.second }.toTypedArray()),
    )
  }
}
