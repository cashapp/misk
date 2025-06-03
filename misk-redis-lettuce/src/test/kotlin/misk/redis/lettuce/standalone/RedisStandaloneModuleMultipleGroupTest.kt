package misk.redis.lettuce.standalone

import com.google.inject.Module
import com.google.inject.name.Named
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisConfig
import misk.redis.lettuce.RedisModule
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.RedisReplicationGroupConfig
import misk.redis.lettuce.redisPort
import misk.redis.lettuce.RedisService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import wisp.deployment.TESTING
import kotlin.test.DefaultAsserter.assertTrue

@MiskTest(startService = true)
@DisplayName("RedisStandaloneModule binding test with multiple replication groups and pooled connections")
internal class RedisStandaloneModuleMultipleGroupTest {
  companion object {
    private const val REPLICATION_GROUP_ID1 = "test-group-001"
    private const val REPLICATION_GROUP_ID2 = "test-group-002"
  }

  private val clientName = "standalone-test-pooled"

  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(
        RedisModule.create(
          config = RedisConfig(
            mapOf(
              REPLICATION_GROUP_ID1 to RedisReplicationGroupConfig(
                client_name = clientName,
                writer_endpoint = RedisNodeConfig(
                  hostname = "localhost",
                  port = redisPort,
                ),
                redis_auth_password = "",
                use_ssl = false,
              ),

              REPLICATION_GROUP_ID2 to RedisReplicationGroupConfig(
                client_name = clientName,
                writer_endpoint = RedisNodeConfig(
                  hostname = "localhost",
                  port = redisPort,
                ),
                redis_auth_password = "",
                use_ssl = false,
              ),
            ),
          )
        ))
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
      }
  }

  @Inject @Named(REPLICATION_GROUP_ID1) lateinit var readWriteConnectionProvider1: ReadWriteConnectionProvider
  @Inject @Named(REPLICATION_GROUP_ID1) lateinit var readOnlyConnectionProvider1: ReadOnlyConnectionProvider
  @Inject @Named(REPLICATION_GROUP_ID2) lateinit var readWriteConnectionProvider2: ReadWriteConnectionProvider
  @Inject @Named(REPLICATION_GROUP_ID2) lateinit var readOnlyConnectionProvider2: ReadOnlyConnectionProvider
  @Inject lateinit var redisService: RedisService
  private val connectionProviders: Map<String, Map<String, StatefulRedisConnectionProvider<String, String>>> by lazy {
    mapOf(
      REPLICATION_GROUP_ID1 to mapOf(
        "readWrite" to readWriteConnectionProvider1,
        "readOnly" to readOnlyConnectionProvider1,
      ),
      REPLICATION_GROUP_ID2 to mapOf(
        "readWrite" to readWriteConnectionProvider2,
        "readOnly" to readOnlyConnectionProvider2,
      ),
    )
  }

  @TestFactory
  fun `verify the ConnectionProvider is POOLED`() =
    connectionProviders.flatMap { (replicationGroupId, providers) ->
      providers.map { (name, provider) ->
        dynamicTest("verify that '$name' from '$replicationGroupId' is POOLED") {
          assertTrue(
            message = "should be a POOLED connection provider",
            actual = provider is PooledStatefulRedisConnectionProvider<String, String>,
          )
        }
      }
    }

  @Test
  fun `test RedisService is started`() {
    assertTrue(
      message = "RedisService should be started",
      actual = redisService.isRunning
    )
  }
}


