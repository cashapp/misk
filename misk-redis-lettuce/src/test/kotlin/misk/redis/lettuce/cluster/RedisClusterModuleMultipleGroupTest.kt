package misk.redis.lettuce.cluster

import com.google.inject.Module
import com.google.inject.name.Named
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisClusterConfig
import misk.redis.lettuce.RedisClusterGroupConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.redisSeedPort
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import wisp.deployment.TESTING
import kotlin.test.DefaultAsserter.assertTrue

@MiskTest(startService = true)
@DisplayName("RedisClusterModule binding test with multiple replication groups and pooled connections")
internal class RedisClusterModuleMultipleGroupTest {
  companion object {
    internal const val REPLICATION_GROUP_ID1 = "test-group-001"
    internal const val REPLICATION_GROUP_ID2 = "test-group-002"
  }

  private val clientName = "standalone-test-pooled"

  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(
        RedisModule.create(
          config = RedisClusterConfig(
            mapOf(
              REPLICATION_GROUP_ID1 to RedisClusterGroupConfig(
                client_name = clientName,
                configuration_endpoint = RedisNodeConfig(
                  hostname = "localhost",
                  port = redisSeedPort,
                ),
                redis_auth_password = "",
                use_ssl = false,
              ),
              REPLICATION_GROUP_ID2 to RedisClusterGroupConfig(
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

  @Inject @Named(REPLICATION_GROUP_ID1) lateinit var clusterConnectionProvider1: ClusterConnectionProvider
  @Inject @Named(REPLICATION_GROUP_ID2) lateinit var clusterConnectionProvider2: ClusterConnectionProvider
  private val connectionProviders: Map<String, StatefulRedisClusterConnectionProvider<String, String>> by lazy {
    mapOf(
      REPLICATION_GROUP_ID1 to clusterConnectionProvider1,
      REPLICATION_GROUP_ID2 to clusterConnectionProvider2,
    )
  }

  @TestFactory
  fun `verify the ConnectionProvider is POOLED`() =
    connectionProviders.map { (replicationGroupId, provider) ->
      dynamicTest("verify that connection provider from '$replicationGroupId' is POOLED") {
        assertTrue(
          message = "should be a POOLED connection provider",
          actual = provider is PooledStatefulRedisClusterConnectionProvider<String, String>,
        )
      }
    }
}
