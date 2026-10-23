package misk.redis.lettuce.standalone

import com.google.inject.Module
import io.kotest.assertions.nondeterministic.eventually
import io.lettuce.core.ClientOptions.DisconnectedBehavior
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisException
import jakarta.inject.Inject
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.RedisReplicationGroupConfig
import misk.redis.lettuce.RedisService
import misk.redis.lettuce.TcpProxy
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis.lettuce.redisPort
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("RedisModule test with a non-essential standalone redis that starts out unreachable")
internal class NonEssentialRedisStandaloneModuleTest {

  private val replicationGroupId = "test-group-001"
  private val proxy = TcpProxy(listenPort = TcpProxy.freePort(), targetPort = redisPort)

  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(
          RedisModule.create(
            config =
              RedisConfig(
                mapOf(
                  replicationGroupId to
                    RedisReplicationGroupConfig(
                      writer_endpoint = RedisNodeConfig(hostname = "127.0.0.1", port = proxy.listenPort),
                      redis_auth_password = "",
                      use_ssl = false,
                      function_code_file_path = "redis/testlib.lua",
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

  @Inject lateinit var client: RedisClient
  @Inject lateinit var metrics: RedisClientMetrics
  @Inject lateinit var redisService: RedisService
  @Inject lateinit var connectionProvider: ReadWriteConnectionProvider

  private val startupVerified
    get() = metrics.startupVerifiedGauge.labels(replicationGroupId).get()

  @AfterEach
  fun tearDown() {
    proxy.close()
  }

  @Test
  fun `starts while Redis is unreachable and connects once it is reachable`(): Unit = runBlocking {
    assertTrue(redisService.isRunning)
    assertEquals(0.0, startupVerified)
    assertFailsWith<ExecutionException> { connectionProvider.acquireBlocking() }

    proxy.start()

    eventually(30.seconds) { assertEquals("PONG", connectionProvider.withConnectionBlocking { ping() }) }
    eventually(30.seconds) { assertEquals(1.0, startupVerified) }
    eventually(30.seconds) {
      assertEquals(
        listOf("testlib"),
        connectionProvider.withConnectionBlocking { functionList().map { it["library_name"] } },
      )
    }
  }

  @Test
  fun `rejects commands while disconnected`(): Unit = runBlocking {
    assertEquals(DisconnectedBehavior.REJECT_COMMANDS, client.options.disconnectedBehavior)
    proxy.start()
    val connection = eventually(30.seconds) { connectionProvider.acquireBlocking() }

    proxy.close()
    eventually(10.seconds) { assertTrue(!connection.isOpen) }

    val e = assertFailsWith<ExecutionException> { connection.async().ping().get(5, TimeUnit.SECONDS) }
    assertIs<RedisException>(e.cause)
  }
}
