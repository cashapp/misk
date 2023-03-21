package misk.redis

import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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
 * [jedisPoolConfig]: Misk-redis is backed by a [JedisPool], you may not want to use the
 * [JedisPoolConfig] defaults! Be sure to understand them!
 *
 * See: https://github.com/xetorthio/jedis/wiki/Getting-started#using-jedis-in-a-multithreaded-environment
 */
class RedisModule(
  private val redisConfig: RedisConfig,
  private val jedisPoolConfig: JedisPoolConfig,
  private val useSsl: Boolean = true,
) : KAbstractModule() {
  override fun configure() {
    bind<RedisConfig>().toInstance(redisConfig)
    install(ServiceModule<RedisService>())
    requireBinding<Metrics>()
  }

  @Provides @Singleton
  internal fun provideRedisClient(
    config: RedisConfig,
    deployment: Deployment,
    metrics: Metrics
  ): Redis {
    // Get the first replication group, we only support 1 replication group per service.
    val replicationGroup = config[config.keys.first()]
      ?: throw RuntimeException("At least 1 replication group must be specified")

    // Create our jedis pool with client-side metrics.
    val clientMetrics = RedisClientMetrics(metrics)
    val jedisPoolWithMetrics = JedisPoolWithMetrics(
      metrics = clientMetrics,
      poolConfig = jedisPoolConfig,
      replicationGroupConfig = replicationGroup,
      ssl = useSsl,
      requiresPassword = deployment.isReal
    )

    return RealRedis(jedisPoolWithMetrics, clientMetrics)
  }
}
