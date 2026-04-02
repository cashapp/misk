package misk.redis

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provider
import misk.logging.getLogger
import redis.clients.jedis.ClientSetInfoConfig
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.UnifiedJedis
import java.time.Duration

/**
 * Controls the connection lifecycle for Redis in cluster mode.
 *
 * This class exists purely to avoid constructing [JedisCluster] in [RedisClusterModule]. The [JedisCluster]
 * constructor, specifically [redis.clients.jedis.providers.ClusterConnectionProvider] within it, is currently hardcoded
 * to make a network call to Redis to hydrate the cluster slot cache. This presents two problems:
 * 1. We should avoid side effects during Guice module configuration
 * 2. Some environments, such as unit tests, do not have a redis to talk to. See
 *    [this link](https://github.com/redis/jedis/wiki/FAQ#how-to-avoid-cluster-initialization-error) for the Jedis
 *    project's current workaround. We chose this route instead as the system property workaround doesn't prevent the
 *    IO, it only suppresses the exception
 */
internal class RedisJedisClusterService(
  private val connectionPoolConfig: ConnectionPoolConfig,
  private val replicationGroup: RedisClusterReplicationGroupConfig,
  private val useSsl: Boolean,
) : AbstractIdleService(), Provider<UnifiedJedis> {
  private lateinit var jedisCluster: JedisCluster

  override fun startUp() {
    check(!::jedisCluster.isInitialized) { "JedisCluster is already initialized. Services must be started only once." }
    logger.info { "Starting ${this::class.simpleName} service" }

    // Create our jedis pool with client-side metrics.
    val jedisClientConfig =
      DefaultJedisClientConfig.builder()
        .connectionTimeoutMillis(replicationGroup.timeout_ms)
        .socketTimeoutMillis(replicationGroup.timeout_ms)
        .password(replicationGroup.redis_auth_password.ifEmpty { null })
        .clientName(replicationGroup.client_name)
        .ssl(useSsl)
        // CLIENT SETINFO is only supported in Redis v7.2+
        .clientSetInfoConfig(ClientSetInfoConfig.DISABLED)
        .build()

    // We want to support services running both under docker and localhost when running locally and this is a way to
    // support that.
    // If a hostname is provided, it will always take precedence over the environment variable.
    val redisHost =
      replicationGroup.configuration_endpoint.hostname?.takeUnless { it.isBlank() }
        ?: System.getenv("REDIS_HOST")
        ?: "127.0.0.1"

    val nodes = setOf(HostAndPort(redisHost, replicationGroup.configuration_endpoint.port))
    val topologyRefreshPeriodMs = replicationGroup.topology_refresh_period_ms

    require(topologyRefreshPeriodMs == null || topologyRefreshPeriodMs > 0) {
      "topology_refresh_period_ms must be positive, got $topologyRefreshPeriodMs"
    }

    jedisCluster = JedisCluster(
      nodes,
      jedisClientConfig,
      connectionPoolConfig,
      topologyRefreshPeriodMs?.let { Duration.ofMillis(it) },
      replicationGroup.max_attempts,
      // Cap total retry wall-clock time: each attempt can take at most one socket timeout.
      Duration.ofMillis(replicationGroup.timeout_ms.toLong() * replicationGroup.max_attempts),
    )
  }

  override fun shutDown() {
    logger.info { "Stopping ${this::class.simpleName} service" }
    jedisCluster.close()
  }

  override fun get(): UnifiedJedis {
    check(::jedisCluster.isInitialized) {
      "JedisCluster is not connected; were Misk services started? " +
        "If this was a test, try setting @MiskTest(startService = true) on the test class."
    }
    return jedisCluster
  }

  companion object {
    private val logger = getLogger<RedisJedisClusterService>()
  }
}
