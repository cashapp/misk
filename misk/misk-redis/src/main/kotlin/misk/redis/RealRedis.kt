package misk.redis

import redis.clients.jedis.Jedis
import java.time.Duration

class RealRedis(private val jedis : Jedis) : Redis {
  override fun del(key: String): Long {
    return jedis.del(key)
  }

  override fun del(vararg keys: String): Long {
    return jedis.del(*keys)
  }

  override fun get(key: String): String? {
    return jedis.get(key)
  }

  override fun set(key: String, value: String): String {
    return jedis.set(key, value)
  }

  override fun setex(key: String, expiryDuration: Duration, value: String): String {
    return jedis.setex(key, expiryDuration.seconds.toInt(), value)
  }
}
