package misk.redis.lettuce.metrics

import com.google.inject.Module
import io.lettuce.core.RedisClient
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.support.AsyncConnectionPoolSupport
import io.lettuce.core.support.BoundedPoolConfig
import jakarta.inject.Inject
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.Test
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.redisPort
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.standalone.PooledStatefulRedisConnectionProvider
import misk.redis.lettuce.standalone.redisClient
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("Verify that the RedisClientMetricsCommandLatencyRecorder records command latencies")
internal class ConnectionPoolMetricsTest {

  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
      }
    }

  @Inject internal lateinit var clientMetrics: RedisClientMetrics
  private val redisUri = redisUri {
    withHost("localhost")
    withPort(redisPort)
    withPassword("".toCharArray())
  }
  private val poolConfig = BoundedPoolConfig.create()

  private lateinit var redisClient: RedisClient
  private lateinit var connectionProvider: PooledStatefulRedisConnectionProvider<String, String>
  private val name = "test"
  private val replicationGroupId = "test-group"

  @BeforeEach
  fun setUp() {
    redisClient = redisClient()
    connectionProvider =
      PooledStatefulRedisConnectionProvider(
        AsyncConnectionPoolSupport.createBoundedObjectPoolAsync(
            { redisClient.connectAsync(StringCodec.UTF8, redisUri) },
            BoundedPoolConfig.create(),
            false, // this is handled directly in the connection provider
          )
          .thenApply {
            clientMetrics.registerConnectionPoolMetrics(name, replicationGroupId, it)
            it
          }
          .toCompletableFuture(),
        replicationGroupId,
      )
  }

  @Test
  fun `test connection pool metrics in RedisClientMetrics`() {
    connectionProvider.acquireBlocking(exclusive = true).use {
      assertEquals(
        "max total connections is ${poolConfig.maxTotal}",
        poolConfig.maxTotal.toDouble(),
        clientMetrics.maxTotalConnectionsGauge.labels(name, replicationGroupId).get(),
      )
      assertEquals(
        "max idle connections is ${poolConfig.maxIdle}",
        poolConfig.maxIdle.toDouble(),
        clientMetrics.maxIdleConnectionsGauge.labels(name, replicationGroupId).get(),
      )
      assertEquals(
        "min idle connections is ${poolConfig.minIdle}",
        poolConfig.minIdle.toDouble(),
        clientMetrics.minIdleConnectionsGauge.labels(name, replicationGroupId).get(),
      )
      assertEquals(
        "active connections is 1 after acquiring a connection",
        1.0,
        clientMetrics.activeConnectionsGauge.labels(name, replicationGroupId).get(),
      )
      assertEquals(
        "idle connections is 0 after acquiring a connection",
        0.0,
        clientMetrics.idleConnectionsGauge.labels(name, replicationGroupId).get(),
      )
    }
    assertEquals(
      "active connections is 0 after closing the connection",
      0.0,
      clientMetrics.activeConnectionsGauge.labels(name, replicationGroupId).get(),
    )
    assertEquals(
      "idle connections is 1 after closing the  connection",
      1.0,
      clientMetrics.idleConnectionsGauge.labels(name, replicationGroupId).get(),
    )
  }
}
