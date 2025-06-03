package misk.redis.lettuce.standalone

import com.google.inject.Module
import io.lettuce.core.RedisClient
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.RedisReplicationGroupConfig
import misk.redis.lettuce.redisPort
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis2.metrics.RedisClientMetricsCommandLatencyRecorder
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import wisp.deployment.TESTING
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue

@MiskTest(startService = true)
@DisplayName("RedisModule test with a single, standalone redis instance with pooled connections")
internal class RedisStandaloneModuleTest {

  private val replicationGroupId = "test-group-001"
  private val clientName = "standalone-test-pooled"

  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(
        RedisModule.create(
          config = RedisConfig(
            mapOf(
              replicationGroupId to RedisReplicationGroupConfig(
                client_name = clientName,
                writer_endpoint = RedisNodeConfig(
                  hostname = "localhost",
                  port = redisPort,
                ),
                redis_auth_password = "",
                use_ssl = false,
              ),

              ),
          ),
        ),
      )
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
    }
  }

  @Inject
  lateinit var client: RedisClient

  @Inject
  lateinit var metrics: RedisClientMetrics

  @Inject
  lateinit var readWriteConnectionProvider: ReadWriteConnectionProvider

  @Inject
  lateinit var readOnlyConnectionProvider: ReadOnlyConnectionProvider
  private val connectionProviders: Map<String, StatefulRedisConnectionProvider<String, String>> by lazy {
    mapOf(
      "readWrite" to readWriteConnectionProvider,
      "readOnly" to readOnlyConnectionProvider,
    )
  }

  @BeforeEach
  fun setUp() {
    readWriteConnectionProvider.withConnectionBlocking { flushall() }
  }


  @TestFactory
  fun `verify the ConnectionProvider is POOLED`() =
    connectionProviders.map { (name, provider) ->
      dynamicTest("verify that '$name' is POOLED") {
        assertTrue(
          message = "should be a POOLED connection provider",
          actual = provider is PooledStatefulRedisConnectionProvider<String, String>,
        )
      }
    }

  @TestFactory
  fun `test client name with ConnectionProvider`() =
    connectionProviders.map { (name, provider) ->
      dynamicTest("test client name with '$name'") {
        assertTrue(
          message = "client name should be set correctly",
          actual = provider.withConnectionBlocking { clientGetname() }
            .startsWith("$replicationGroupId:$clientName:$name:"),
        )
      }
    }


  @TestFactory
  fun `test ping with ConnectionProvider`() =
    connectionProviders.map { (name, provider) ->
      dynamicTest("test ping with '$name'") {
        assertEquals(
          message = "result is PONG",
          expected = "PONG",
          actual = provider.withConnectionBlocking { ping() },
        )
      }
    }

  @Test
  fun `test redis client has CommandLatencyRecorder registered`() {
    assertTrue(
      message = "should have CommandLatencyRecorder registered",
      actual = client.resources.commandLatencyRecorder() is RedisClientMetricsCommandLatencyRecorder,
    )
  }

  @TestFactory
  fun `verify that the pooled connection provider is registered in the RedisClientMetrics`() {
    connectionProviders.map { (name, connectionProvider) ->
      dynamicTest("testthe connection provider '$name' is registered in the RedisClientMetrics ") {
        val providerPool =
          (connectionProvider as PooledStatefulRedisConnectionProvider<String, String>).poolFuture.get()
        val metricsReference =
          metrics.maxTotalConnectionsGauge.labels(clientName, replicationGroupId).reference
        assertTrue(
          message = "pool in '$clientName' should be registered in the RedisClientMetrics",
          actual = metricsReference.get() === providerPool,
        )
      }
    }
  }
}

