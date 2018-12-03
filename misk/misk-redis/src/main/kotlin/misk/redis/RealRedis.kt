package misk.redis

import redis.clients.jedis.Jedis

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

  override fun setex(key: String, seconds: Int, value: String): String {
    return jedis.setex(key, seconds, value)
  }
}
