package misk.redis.lettuce.cluster

import com.google.inject.Module
import io.kotest.assertions.nondeterministic.eventually
import io.lettuce.core.ClientOptions.DisconnectedBehavior
import io.lettuce.core.cluster.RedisClusterClient
import jakarta.inject.Inject
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisClusterConfig
import misk.redis.lettuce.RedisClusterGroupConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.RedisService
import misk.redis.lettuce.TcpProxy
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis.lettuce.redisSeedPort
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("RedisModule test with a non-essential redis cluster that starts out unreachable")
internal class NonEssentialRedisClusterModuleTest {

  private val replicationGroupId = "test-group-001"
  private val proxy = TcpProxy(listenPort = TcpProxy.freePort(), targetPort = redisSeedPort)

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
                      configuration_endpoint = RedisNodeConfig(hostname = "127.0.0.1", port = proxy.listenPort),
                      redis_auth_password = "",
                      use_ssl = false,
                    )
                )
              ),
            nonEssential = true,
          )
        )
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
      }
    }

  @Inject lateinit var client: RedisClusterClient
  @Inject lateinit var metrics: RedisClientMetrics
  @Inject lateinit var redisService: RedisService
  @Inject lateinit var connectionProvider: ClusterConnectionProvider

  @AfterEach
  fun tearDown() {
    proxy.close()
  }

  @Test
  fun `starts while Redis is unreachable and connects once it is reachable`(): Unit = runBlocking {
    assertTrue(redisService.isRunning)
    assertEquals(DisconnectedBehavior.REJECT_COMMANDS, client.options.disconnectedBehavior)
    assertEquals(0.0, metrics.startupVerifiedGauge.labels(replicationGroupId).get())
    assertFailsWith<ExecutionException> { connectionProvider.acquireBlocking() }

    proxy.start()

    eventually(30.seconds) { assertEquals("PONG", connectionProvider.withConnectionBlocking { ping() }) }
    eventually(30.seconds) { assertEquals(1.0, metrics.startupVerifiedGauge.labels(replicationGroupId).get()) }
  }
}
