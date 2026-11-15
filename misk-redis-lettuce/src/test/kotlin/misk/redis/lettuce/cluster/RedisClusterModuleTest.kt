package misk.redis.lettuce.cluster

import com.google.inject.Module
import io.lettuce.core.cluster.RedisClusterClient
import jakarta.inject.Inject
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisClusterConfig
import misk.redis.lettuce.RedisClusterGroupConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.RedisService
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis.lettuce.redisSeedPort
import misk.redis2.metrics.RedisClientMetricsCommandLatencyRecorder
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.DisplayName
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("RedisModule test with a single redis cluster and pooled connections")
internal class RedisClusterModuleTest {

  private val replicationGroupId = "test-group-001"
  private val clientName = "cluster-test-pooled"

  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(
          RedisModule.create(
            config =
              RedisClusterConfig(
                mapOf(
                  replicationGroupId to
                    RedisClusterGroupConfig(
                      client_name = clientName,
                      configuration_endpoint = RedisNodeConfig(hostname = "localhost", port = redisSeedPort),
                      redis_auth_password = "",
                      use_ssl = false,
                      function_code_file_path = "redis/testlib.lua",
                    )
                )
              )
          )
        )
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
      }
    }

  @Inject lateinit var client: RedisClusterClient
  @Inject lateinit var redisService: RedisService
  @Inject lateinit var metrics: RedisClientMetrics
  @Inject lateinit var connectionProvider: ClusterConnectionProvider

  @Test
  fun `verify the connectionProvider is a POOLED`() {
    assertTrue(
      "should be a POOLED connection provider",
      connectionProvider is PooledStatefulRedisClusterConnectionProvider<String, String>,
    )
  }

  @Test
  fun `test client name with ConnectionProvider`() {
    assertTrue(
      message = "client name should be set correctly",
      actual =
        connectionProvider.withConnectionBlocking { clientGetname() }.startsWith("$replicationGroupId:$clientName:"),
    )
  }

  @Test
  fun `test ping with connectionProvider`() {
    assertEquals(
      message = "result is PONG",
      expected = "PONG",
      actual = connectionProvider.withConnectionBlocking { ping() },
    )
  }

  @Test
  fun `test redis client has CommandLatencyRecorder registered`() {
    assertTrue(
      message = "RedisClient should have CommandLatencyRecorder registered",
      actual = client.resources.commandLatencyRecorder() is RedisClientMetricsCommandLatencyRecorder,
    )
  }

  @Test
  fun `verify that the pooled connection provider is registered in the RedisClientMetrics`() {
    val providerPool =
      (connectionProvider as PooledStatefulRedisClusterConnectionProvider<String, String>).poolFuture.get()
    val metricsReference = metrics.maxTotalConnectionsGauge.labels(clientName, replicationGroupId).reference
    assertTrue(
      message = "pool in '$clientName' should be registered in the RedisClientMetrics",
      actual = metricsReference.get() === providerPool,
    )
  }

  @Test
  fun `test RedisService is started`() {
    assertTrue(message = "RedisService should be started", actual = redisService.isRunning)
  }

  @Test
  fun `verify RedisService loaded function code`() {
    connectionProvider.withConnectionBlocking {
      assertTrue(
        message = "should have testlib registered",
        functionList().let { functions: MutableList<MutableMap<String, Any>> ->
          functions.size == 1 && functions.first()["library_name"] == "testlib"
        },
      )
    }
  }
}
