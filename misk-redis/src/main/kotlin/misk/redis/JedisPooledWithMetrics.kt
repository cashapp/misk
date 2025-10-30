package misk.redis

import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.PooledObjectFactory
import redis.clients.jedis.ClientSetInfoConfig
import redis.clients.jedis.Connection
import redis.clients.jedis.ConnectionFactory
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisClientConfig
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.providers.PooledConnectionProvider

internal class JedisPooledWithMetrics(
  metrics: RedisClientMetrics,
  poolConfig: ConnectionPoolConfig,
  replicationGroupConfig: RedisReplicationGroupConfig,
  ssl: Boolean = true,
  requiresPassword: Boolean = true,
  database: Int? = null,
) : JedisPooled(
  PooledConnectionProviderWithMetrics(
    metrics,
    poolConfig,
    ConnectionFactoryWithMetrics(metrics, replicationGroupConfig, ssl, requiresPassword, database)
  )
)

private class ConnectionFactoryWithMetrics(
  private val metrics: RedisClientMetrics,
  replicationGroupConfig: RedisReplicationGroupConfig,
  ssl: Boolean = true,
  requiresPassword: Boolean = true,
  database: Int? = null,
) : ConnectionFactory(
  HostAndPort(
    replicationGroupConfig.writer_endpoint.hostname?.takeUnless { it.isBlank() } ?: System.getenv("REDIS_HOST") ?: "127.0.0.1",
    replicationGroupConfig.writer_endpoint.port
  ),
  createJedisClientConfig(replicationGroupConfig, ssl, requiresPassword, database),
) {

  override fun destroyObject(pooledJedis: PooledObject<Connection>) {
    metrics.destroyedConnectionsCounter.inc()
    super.destroyObject(pooledJedis)
  }
}

private fun createJedisClientConfig(
  replicationGroupConfig: RedisReplicationGroupConfig,
  ssl: Boolean,
  requiresPassword: Boolean = true,
  database: Int? = null,
): JedisClientConfig {

  return DefaultJedisClientConfig.builder()
    .connectionTimeoutMillis(replicationGroupConfig.timeout_ms)
    .socketTimeoutMillis(replicationGroupConfig.timeout_ms)
    .password(replicationGroupConfig.redis_auth_password
      .ifEmpty {
        check(!requiresPassword) {
          "This Redis client is configured to require an auth password, but none was provided!"
        }
        null
      })
    .let { config -> database?.let { config.database(it) } ?: config }
    .ssl(ssl)
    //CLIENT SETINFO is only supported in Redis v7.2+
    .clientSetInfoConfig(ClientSetInfoConfig.DISABLED)
    .build()
}

private class PooledConnectionProviderWithMetrics(
  private val metrics: RedisClientMetrics,
  poolConfig: ConnectionPoolConfig,
  factory: PooledObjectFactory<Connection>?
) : PooledConnectionProvider(factory, poolConfig) {

  init {
    metrics.maxTotalConnectionsGauge.set(this.pool.maxTotal.toDouble())
    metrics.maxIdleConnectionsGauge.set(this.pool.maxIdle.toDouble())
    metrics.setActiveIdleConnectionMetrics(this.pool)
  }

  override fun close() {
    super.close().also {
      metrics.setActiveIdleConnectionMetrics(this.pool)
    }
  }

  override fun getConnection(): Connection {
    return super.getConnection().also {
      metrics.setActiveIdleConnectionMetrics(this.pool)
    }
  }

}

