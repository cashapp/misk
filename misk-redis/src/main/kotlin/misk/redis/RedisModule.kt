package misk.redis

import com.google.common.base.Ticker
import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPooled
import wisp.deployment.Deployment

/**
 * Configures a [Redis] client with metrics, this also installs a [ServiceModule] for [RedisService].
 * If other services require a working client connection to Redis before they can be used, specify a
 * dependency like:
 *
 * ```
 * install(ServiceModule<MyService>()
 *     .dependsOn(keyOf<RedisService>())
 * )
 * ```
 *
 * You must pass in configuration for your Redis client.
 *
 * [redisConfig]: Only one replication group config is supported; this module will use the first
 * configuration it finds. An empty [RedisReplicationGroupConfig.redis_auth_password] is only
 * permitted in fake environments. See [Deployment].
 *
 * [connectionPoolConfig]: Misk-redis is backed by a [JedisPooled], you may not want to use the
 * [ConnectionPoolConfig] defaults! Be sure to understand them!
 *
 * See: https://github.com/xetorthio/jedis/wiki/Getting-started#using-jedis-in-a-multithreaded-environment
 */
class RedisModule @JvmOverloads constructor(
  private val redisConfig: RedisConfig,
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = true,
) : KAbstractModule() {

  @Deprecated("Use ConnectionPoolConfig instead of JedisPoolConfig")
  constructor(
    redisConfig: RedisConfig,
    jedisPoolConfig: JedisPoolConfig,
    useSsl: Boolean = true,
  ) : this(
    redisConfig,
    connectionPoolConfig = jedisPoolConfig.toConnectionPoolConfig(),
    useSsl = useSsl
  )

  override fun configure() {
    bind<RedisConfig>().toInstance(redisConfig)
    install(ServiceModule<RedisService>().enhancedBy<ReadyService>())
    requireBinding<Metrics>()
  }

  @Provides @Singleton
  internal fun provideRedisClient(
    config: RedisConfig,
    deployment: Deployment,
    metrics: Metrics,
    ticker: Ticker,
  ): Redis {
    // Get the first replication group, we only support 1 replication group per service.
    val replicationGroup = config[config.keys.first()]
      ?: throw RuntimeException("At least 1 replication group must be specified")

    // Create our jedis pool with client-side metrics.
    val clientMetrics = RedisClientMetrics(ticker, metrics)
    val jedisPooledWithMetrics = JedisPooledWithMetrics(
      metrics = clientMetrics,
      poolConfig = connectionPoolConfig,
      replicationGroupConfig = replicationGroup,
      ssl = useSsl,
      requiresPassword = deployment.isReal
    )

    return RealRedis(jedisPooledWithMetrics, clientMetrics)
  }

}

private fun JedisPoolConfig.toConnectionPoolConfig() = ConnectionPoolConfig().apply {
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
