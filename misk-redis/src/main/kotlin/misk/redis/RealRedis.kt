package misk.redis

import redis.clients.jedis.JedisPool
import java.time.Duration

/** For each command, a Jedis instance is retrieved from the pool and returned once the command has been issued. */
class RealRedis(private val jedisPool: JedisPool) : Redis {
  override fun del(key: String): Boolean {
    jedisPool.resource.use { jedis ->
      return (jedis.del(key) == 1L)
    }
  }

  override fun del(vararg keys: String): Int {
    jedisPool.resource.use { jedis ->
      return jedis.del(*keys).toInt()
    }
  }

  override fun get(key: String): String? {
    jedisPool.resource.use { jedis ->
      return jedis.get(key)
    }
  }

  override fun set(key: String, value: String): String {
    jedisPool.resource.use { jedis ->
      return jedis.set(key, value)
    }
  }

  override fun set(key: String, expiryDuration: Duration, value: String): String {
    jedisPool.resource.use { jedis ->
      return jedis.setex(key, expiryDuration.seconds.toInt(), value)
    }
  }

  /** Closes the connection to Redis. */
  fun close() {
    return jedisPool.close()
  }
}
