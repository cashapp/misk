package misk.redis

import org.apache.commons.pool2.PooledObject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

internal class JedisPoolWithMetrics(
  private val metrics: RedisClientMetrics,
  poolConfig: JedisPoolConfig,
  replicationGroupConfig: RedisReplicationGroupConfig,
  ssl: Boolean = true,
  requiresPassword: Boolean = true,
) : JedisPool(
  poolConfig,
  JedisFactoryWithMetrics(metrics, replicationGroupConfig, ssl, requiresPassword)
) {
  init {
    metrics.maxTotalConnectionsGauge.set(this.maxTotal.toDouble())
    metrics.maxIdleConnectionsGauge.set(this.maxIdle.toDouble())
  }

  override fun getResource(): Jedis {
    metrics.activeConnectionsGauge.set(this.numActive.toDouble())
    metrics.idleConnectionsGauge.set(this.numIdle.toDouble())
    return super.getResource()
  }

  private class JedisFactoryWithMetrics(
    private val metrics: RedisClientMetrics,
    replicationGroupConfig: RedisReplicationGroupConfig,
    ssl: Boolean = true,
    requiresPassword: Boolean = true,
  ) : JedisFactory(
    /* host = */ replicationGroupConfig.writer_endpoint.hostname,
    /* port = */ replicationGroupConfig.writer_endpoint.port,
    /* connectionTimeout = */ replicationGroupConfig.timeout_ms,
    /* soTimeout = */ replicationGroupConfig.timeout_ms,
    /* password = */ replicationGroupConfig.redis_auth_password
    .ifEmpty {
      check(!requiresPassword) {
        "This Redis client is configured to require an auth password, but none was provided!"
      }
      null
    },
    /* database = */ Protocol.DEFAULT_DATABASE,
    /* clientName = */ null,
    /* ssl = */ ssl,
    /* sslSocketFactory = */ null,
    /* sslParameters = */ null,
    /* hostnameVerifier = */ null
  ) {
    override fun destroyObject(pooledJedis: PooledObject<Jedis>) {
      metrics.destroyedConnectionsCounter.inc()
      super.destroyObject(pooledJedis)
    }
  }
}
