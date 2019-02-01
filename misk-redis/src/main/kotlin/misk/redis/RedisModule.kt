package misk.redis

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

/**
 * Configures a JedisPool to connect to a Redis instance. The use of a JedisPool ensures thread safety.
 * See: https://github.com/xetorthio/jedis/wiki/Getting-started#using-jedis-in-a-multithreaded-environment
 */
class RedisModule(private val jedisPoolConfig: JedisPoolConfig) : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<RedisService>()
  }

  @Provides @Singleton
  internal fun provideRedisClient(config: RedisConfig): Redis {
    // Get the first replication group, we only support 1 replication group per service
    val replicationGroup = config[config.keys.first()]
      ?: throw RuntimeException("At least 1 replication group must be specified")

    // Create our jedis pool
    val jedisPool = JedisPool(
      jedisPoolConfig,
      replicationGroup.writer_endpoint.hostname,
      replicationGroup.writer_endpoint.port,
      true
    )

    // Authenticate with the redis server
    jedisPool.resource.use {
      it.auth(replicationGroup.redis_auth_password)
    }

    return RealRedis(jedisPool)
  }
}
