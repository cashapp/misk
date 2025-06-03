package misk.redis.lettuce.cluster

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisClusterConfig
import misk.redis.lettuce.RedisClusterGroupConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.redisSeedPort
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.DisplayName
import wisp.deployment.TESTING
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test

@MiskTest(startService = true)
@DisplayName("RedisModule test with a single redis cluster and pooled connections")
internal class RedisClusterModuleTest {

  private val replicationGroupId = "test-group-001"
  private val clientName = "cluster-test-pooled"

  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(
        RedisModule.create(
          config = RedisClusterConfig(
            mapOf(
              replicationGroupId to RedisClusterGroupConfig(
                client_name = clientName,
                configuration_endpoint = RedisNodeConfig(
                  hostname = "localhost",
                  port = redisSeedPort,
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
      actual = connectionProvider.withConnectionBlocking { clientGetname() }
        .startsWith("$replicationGroupId:$clientName:"),
    )
  }

  @Test
  fun `test ping with connectionProvider`() {
    assertEquals(
      message = "result is PONG",
      expected = "PONG",
      actual = connectionProvider.withConnectionBlocking { ping() }
    )
  }
}

