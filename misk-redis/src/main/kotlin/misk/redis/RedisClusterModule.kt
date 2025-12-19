package misk.redis

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.metrics.v2.Metrics
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.UnifiedJedis
import wisp.deployment.Deployment

/**
 * Configures a [Redis] client that interacts with a Redis cluster. This also installs a [ServiceModule] for
 * [RedisService].
 *
 * To use this, install a [RedisClusterModule] and add a corresponding [RedisClusterConfig] to your application’s config
 * YAML.
 *
 * If other services require a working client connection to Redis before they can be used, specify a dependency like:
 * ```
 * install(ServiceModule<MyService>()
 *     .dependsOn(keyOf<RedisService>())
 * )
 * ```
 *
 * [redisClusterGroupConfig]: Only one replication group config is supported. An empty
 * [RedisReplicationGroupConfig.redis_auth_password] is only permitted in fake environments. See [Deployment].
 *
 * This initiates a [JedisCluster] which automatically discovers the topology of the Redis cluster, and routes commands
 * to the appropriate node based on the hash slot of the key.
 *
 * Note: This has some limitations regarding multi-key operations that involve keys belonging to different slots. Some
 * unsupported functions in [JedisCluster] were addressed in this custom wrapper (e.g. `mset`, `mget` and `del`) but not
 * the atomic operations such as `rpoplpush`, `lmove`, `brpoplpush` etc. as it is not recommended. For more information,
 * refer to the following links:
 *
 * https://redis.io/docs/reference/cluster-spec/ https://redis.com/blog/redis-clustering-best-practices-with-keys/
 */
class RedisClusterModule
@JvmOverloads
constructor(
  private val redisClusterGroupConfig: RedisClusterReplicationGroupConfig,
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = true,
) : KAbstractModule() {

  @Deprecated("Please use RedisClusterReplicationGroupConfig to pass specific redis cluster configuration.")
  constructor(
    redisClusterConfig: RedisClusterConfig,
    connectionPoolConfig: ConnectionPoolConfig,
    useSsl: Boolean = true,
  ) : this(
    // Get the first replication group, we only support 1 replication group per service.
    redisClusterConfig.values.firstOrNull() ?: throw RuntimeException("At least 1 replication group must be specified"),
    connectionPoolConfig = connectionPoolConfig,
    useSsl = useSsl,
  )

  override fun configure() {
    bind<RedisClusterReplicationGroupConfig>().toInstance(redisClusterGroupConfig)

    // Bind the redis service to a one-off provider - doing this here instead of annotating the class with @Singleton
    // primarily to avoid injecting the useSsl boolean via @Named or similar
    bind(keyOf<RedisJedisClusterService>())
      .toProvider { RedisJedisClusterService(connectionPoolConfig, redisClusterGroupConfig, useSsl) }
      .asSingleton()
    // The services, in addition to normal lifecycle management, provide the actual clients
    bind<UnifiedJedis>().toProvider(keyOf<RedisJedisClusterService>())
    bind<Redis>().toProvider(keyOf<RedisFacadeClusterService>())

    // RedisFacadeClusterService must depend on the Jedis service, as it provides the UnifiedJedis
    // that RedisFacadeClusterService uses to build RealRedis
    install(ServiceModule<RedisJedisClusterService>().enhancedBy<ReadyService>())
    install(ServiceModule<RedisFacadeClusterService>().dependsOn<RedisJedisClusterService>().enhancedBy<ReadyService>())
    requireBinding<Metrics>()
  }
}
