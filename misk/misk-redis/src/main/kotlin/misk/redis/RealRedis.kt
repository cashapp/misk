package misk.redis

import redis.clients.jedis.Jedis
import java.time.Duration

class RealRedis(private val jedis : Jedis) : Redis {
  override fun del(key: String): Boolean {
    return (jedis.del(key) == 1L)
  }

  override fun del(vararg keys: String): Int {
    return jedis.del(*keys).toInt()
  }

  override fun get(key: String): String? {
    return jedis.get(key)
  }

  override fun set(key: String, value: String): String {
    return jedis.set(key, value)
  }

  override fun set(key: String, expiryDuration: Duration, value: String): String {
    return jedis.setex(key, expiryDuration.seconds.toInt(), value)
  }

  /** Closes the connection to Redis. */
  fun close() {
    return jedis.close()
  }
}
