package misk.redis

import com.google.common.base.Ticker
import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import wisp.deployment.Deployment

/**
 * Configures a [Redis] client that interacts with a Redis cluster. This also installs a
 * [ServiceModule] for [RedisService].
 *
 * To use this, install a [RedisClusterModule] and add a corresponding [RedisClusterConfig] to your
 * application’s config YAML.
 *
 * If other services require a working client connection to Redis before they can be used, specify a
 * dependency like:
 *
 * ```
 * install(ServiceModule<MyService>()
 *     .dependsOn(keyOf<RedisService>())
 * )
 * ```
 *
 *
 * [redisClusterConfig]: Only one replication group config is supported; this module will use the first
 * configuration it finds. An empty [RedisReplicationGroupConfig.redis_auth_password] is only
 * permitted in fake environments. See [Deployment].
 *
 * This initiates a [JedisCluster] which automatically discovers the topology of the Redis cluster,
 * and routes commands to the appropriate node based on the hash slot of the key.
 *
 * Note: This has some limitations regarding multi-key operations that involve keys belonging to
 * different slots. Some unsupported functions in [JedisCluster] were addressed in this custom
 * wrapper (e.g. `mset`, `mget` and `del`) but not the atomic operations such as `rpoplpush`, `lmove`,
 * `brpoplpush` etc. as it is not recommended. For more information, refer to the following links:
 *
 * https://redis.io/docs/reference/cluster-spec/
 * https://redis.com/blog/redis-clustering-best-practices-with-keys/
 */
class RedisClusterModule @JvmOverloads constructor(
  private val redisClusterConfig: RedisClusterConfig,
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = true
) : KAbstractModule() {

  override fun configure() {
    bind<RedisClusterConfig>().toInstance(redisClusterConfig)
    install(ServiceModule<RedisService>().enhancedBy<ReadyService>())
    requireBinding<Metrics>()
  }

  @Provides @Singleton
  internal fun provideRedisClusterClient(
    config: RedisClusterConfig,
    deployment: Deployment,
    metrics: Metrics,
    ticker: Ticker,
  ): Redis {
    // Get the first replication group, we only support 1 replication group per service.
    val replicationGroup = config[config.keys.first()]
      ?: throw RuntimeException("At least 1 replication group must be specified")

    // Create our jedis pool with client-side metrics.
    val clientMetrics = RedisClientMetrics(ticker, metrics)

    val jedisCluster = JedisCluster(
      HostAndPort(
        replicationGroup.configuration_endpoint.hostname,
        replicationGroup.configuration_endpoint.port
      ),
      replicationGroup.timeout_ms,
      replicationGroup.timeout_ms,
      replicationGroup.max_attempts,
      replicationGroup.redis_auth_password.ifEmpty {
        check(!deployment.isReal) { "Redis auth password cannot be empty in a real environment!" }
        null
      },
      replicationGroup.client_name,
      connectionPoolConfig,
      useSsl
    )

    return RealRedis(jedisCluster, clientMetrics)
  }
}
