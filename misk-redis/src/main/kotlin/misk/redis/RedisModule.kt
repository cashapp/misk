package misk.redis

import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.UnifiedJedis
import wisp.deployment.Deployment

/**
 * Configures a [Redis] client with metrics, this also installs a [ServiceModule] for [RedisService]. If other services
 * require a working client connection to Redis before they can be used, specify a dependency like:
 * ```
 * install(ServiceModule<MyService>()
 *     .dependsOn(keyOf<RedisService>())
 * )
 * ```
 *
 * You must pass in configuration for your Redis client.
 *
 * [redisReplicationGroupConfig]: Only one replication group config is supported. An empty
 * [RedisReplicationGroupConfig.redis_auth_password] is only permitted in fake environments. See [Deployment].
 *
 * [connectionPoolConfig]: Misk-redis is backed by a [JedisPooled], you may not want to use the [ConnectionPoolConfig]
 * defaults! Be sure to understand them!
 *
 * See: https://github.com/xetorthio/jedis/wiki/Getting-started#using-jedis-in-a-multithreaded-environment
 */
class RedisModule
@JvmOverloads
constructor(
  private val redisReplicationGroupConfig: RedisReplicationGroupConfig,
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = true,
) : KAbstractModule() {

  @Deprecated("Use ConnectionPoolConfig instead of JedisPoolConfig")
  constructor(
    redisConfig: RedisConfig,
    jedisPoolConfig: JedisPoolConfig,
    useSsl: Boolean = true,
  ) : this(
    getFirstReplicationGroup(redisConfig),
    connectionPoolConfig = jedisPoolConfig.toConnectionPoolConfig(),
    useSsl = useSsl,
  )

  @Deprecated("Please use RedisReplicationGroupConfig to pass specific redis cluster configuration.")
  constructor(
    redisConfig: RedisConfig,
    connectionPoolConfig: ConnectionPoolConfig,
    useSsl: Boolean = true,
  ) : this(getFirstReplicationGroup(redisConfig), connectionPoolConfig = connectionPoolConfig, useSsl = useSsl)

  override fun configure() {
    bind<RedisReplicationGroupConfig>().toInstance(redisReplicationGroupConfig)
    install(ServiceModule<RedisService>().enhancedBy<ReadyService>())
    requireBinding<Metrics>()
  }

  @Provides
  @Singleton
  internal fun provideRedisClient(clientMetrics: RedisClientMetrics, unifiedJedis: UnifiedJedis): Redis =
    RealRedis(unifiedJedis, clientMetrics)

  @Provides
  @Singleton
  internal fun provideUnifiedJedis(
    clientMetrics: RedisClientMetrics,
    redisReplicationGroupConfig: RedisReplicationGroupConfig,
    deployment: Deployment,
  ): UnifiedJedis {

    return JedisPooledWithMetrics(
      metrics = clientMetrics,
      poolConfig = connectionPoolConfig,
      replicationGroupConfig = redisReplicationGroupConfig,
      ssl = useSsl,
      requiresPassword = deployment.isReal,
    )
  }

  companion object {
    private fun getFirstReplicationGroup(redisConfig: RedisConfig): RedisReplicationGroupConfig {
      // Get the first replication group, we only support 1 replication group per service.
      return redisConfig.values.firstOrNull()
        ?: throw RuntimeException("At least 1 replication group must be specified")
    }
  }
}

private fun JedisPoolConfig.toConnectionPoolConfig() =
  ConnectionPoolConfig().apply {
    maxTotal = this@toConnectionPoolConfig.maxTotal
    maxIdle = this@toConnectionPoolConfig.maxIdle
    minIdle = this@toConnectionPoolConfig.minIdle
    blockWhenExhausted = this@toConnectionPoolConfig.blockWhenExhausted
    testOnCreate = this@toConnectionPoolConfig.testOnCreate
    testOnBorrow = this@toConnectionPoolConfig.testOnBorrow
    testOnReturn = this@toConnectionPoolConfig.testOnReturn
    testWhileIdle = this@toConnectionPoolConfig.testWhileIdle
    timeBetweenEvictionRuns = this@toConnectionPoolConfig.durationBetweenEvictionRuns
    minEvictableIdleTime = this@toConnectionPoolConfig.minEvictableIdleDuration
    setMaxWait(this@toConnectionPoolConfig.maxWaitDuration)
  }
