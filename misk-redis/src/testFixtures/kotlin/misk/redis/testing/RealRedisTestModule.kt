package misk.redis.testing

import com.google.inject.Provides
import com.google.inject.util.Modules
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.redis.JedisPooledWithMetrics
import misk.redis.RedisClientMetrics
import misk.redis.RedisModule
import misk.redis.RedisReplicationGroupConfig
import misk.testing.parallelTestIndex
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.UnifiedJedis
import wisp.deployment.Deployment

/**
 * Installs a real redis for testing with support for parallel tests.
 */
class RealRedisTestModule(
  private val redisReplicationGroupConfig: RedisReplicationGroupConfig,
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = false,
) : KAbstractModule() {
  override fun configure() {
    install(
      Modules.override(
        RedisModule(redisReplicationGroupConfig, connectionPoolConfig, useSsl)
      ).with(TestUnifiedJedisModule(connectionPoolConfig, useSsl))
    )
    install(RedisTestFlushModule())
  }
}

private class TestUnifiedJedisModule(
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = true,
) : KAbstractModule() {
  @Provides @Singleton
  internal fun provideUnifiedJedis(
    clientMetrics: RedisClientMetrics,
    redisReplicationGroupConfig: RedisReplicationGroupConfig,
  ): UnifiedJedis {
    val database = parallelTestIndex()
    return JedisPooledWithMetrics(
      metrics = clientMetrics,
      poolConfig = connectionPoolConfig,
      replicationGroupConfig = redisReplicationGroupConfig,
      ssl = useSsl,
      requiresPassword = false,
      database = database
    )
  }
}
