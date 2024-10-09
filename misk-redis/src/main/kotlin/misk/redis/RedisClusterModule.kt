package misk.redis

import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import redis.clients.jedis.ClientSetInfoConfig
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.UnifiedJedis
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
 * [redisClusterGroupConfig]: Only one replication group config is supported.
 * An empty [RedisReplicationGroupConfig.redis_auth_password] is only permitted in fake
 * environments. See [Deployment].
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
  private val redisClusterGroupConfig: RedisClusterReplicationGroupConfig,
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val useSsl: Boolean = true
) : KAbstractModule() {

  @Deprecated("Please use RedisClusterReplicationGroupConfig to pass specific redis cluster configuration.")
  constructor(
    redisClusterConfig: RedisClusterConfig,
    connectionPoolConfig: ConnectionPoolConfig,
    useSsl: Boolean = true,
  ) : this(
    // Get the first replication group, we only support 1 replication group per service.
    redisClusterConfig.values.firstOrNull()
      ?: throw RuntimeException("At least 1 replication group must be specified"),
    connectionPoolConfig = connectionPoolConfig,
    useSsl = useSsl,
  )

  override fun configure() {
    bind<RedisClusterReplicationGroupConfig>().toInstance(redisClusterGroupConfig)
    install(ServiceModule<RedisService>().enhancedBy<ReadyService>())
    requireBinding<Metrics>()
  }

  @Provides @Singleton
  internal fun provideRedisClusterClient(
    clientMetrics: RedisClientMetrics,
    unifiedJedis: UnifiedJedis
  ): Redis = RealRedis(unifiedJedis, clientMetrics)

  @Provides @Singleton
  internal fun provideUnifiedJedis(
    replicationGroup: RedisClusterReplicationGroupConfig,
    deployment: Deployment
  ): UnifiedJedis {

    // Create our jedis pool with client-side metrics.
    val jedisClientConfig = DefaultJedisClientConfig.builder()
      .connectionTimeoutMillis(replicationGroup.timeout_ms)
      .socketTimeoutMillis(replicationGroup.timeout_ms)
      .password(replicationGroup.redis_auth_password
        .ifEmpty {
          check(!deployment.isReal) {
            "This Redis client is configured to require an auth password, but none was provided!"
          }
          null
        }
      )
      .clientName(replicationGroup.client_name)
      .ssl(useSsl)
      //CLIENT SETINFO is only supported in Redis v7.2+
      .clientSetInfoConfig(ClientSetInfoConfig.DISABLED)
      .build()

    // We want to support services running both under docker and localhost when running locally and this is a way to support that.
    // If a hostname is provided, it will always take precedence over the environment variable.
    val redisHost = replicationGroup.configuration_endpoint.hostname?.takeUnless { it.isNullOrBlank() } ?: System.getenv("REDIS_HOST") ?: "127.0.0.1"

    return JedisCluster(
      setOf(
        HostAndPort(
          redisHost,
          replicationGroup.configuration_endpoint.port
        )
      ),
      jedisClientConfig,
      replicationGroup.max_attempts,
      connectionPoolConfig
    )
  }
}
