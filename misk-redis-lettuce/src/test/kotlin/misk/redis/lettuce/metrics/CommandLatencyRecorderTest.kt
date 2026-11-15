package misk.redis.lettuce.metrics

import com.google.inject.Module
import io.lettuce.core.RedisClient
import jakarta.inject.Inject
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.Test
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.FIRST_RESPONSE_TIME
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.OPERATION_TIME
import misk.redis.lettuce.redisPort
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.standalone.clientResources
import misk.redis.lettuce.standalone.redisClient
import misk.redis.lettuce.standalone.withConnectionBlocking
import misk.redis2.metrics.RedisClientMetricsCommandLatencyRecorder
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("Verify that the RedisClientMetricsCommandLatencyRecorder records command latencies")
internal class CommandLatencyRecorderTest {

  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
      }
    }

  @Inject internal lateinit var clientMetrics: RedisClientMetrics
  private lateinit var redisClient: RedisClient

  @BeforeEach
  fun setUp() {
    redisClient =
      redisClient(
        redisURI =
          redisUri {
            withHost("localhost")
            withPort(redisPort)
            withPassword("".toCharArray())
          },
        clientResources =
          clientResources {
            commandLatencyRecorder(
              RedisClientMetricsCommandLatencyRecorder(
                replicationGroupId = "test_replication_group_001",
                clientMetrics = clientMetrics,
              )
            )
          },
      )
  }

  @Test
  fun `test ping command has first response time latencies registered`() {
    redisClient.withConnectionBlocking {
      assertEquals("result is PONG", "PONG", sync().ping())
      clientMetrics.firstResponseTime.collect().also {
        assert(it.size == 1) { "Expected 1 operation time metric" }
        it.first().also { metricFamilySamples ->
          assert(metricFamilySamples.name == FIRST_RESPONSE_TIME) { "Expected histogram named $FIRST_RESPONSE_TIME" }
        }
      }
    }
  }

  @Test
  fun `test ping command has operation time latencies registered`() {
    redisClient.withConnectionBlocking {
      assertEquals("result is PONG", "PONG", sync().ping())
      clientMetrics.operationTime.collect().also {
        assert(it.size == 1) { "Expected 1 operation time metric" }
        it.first().also { metricFamilySamples ->
          assert(metricFamilySamples.name == OPERATION_TIME) { "Expected histogram named $OPERATION_TIME" }
        }
      }
    }
  }
}
