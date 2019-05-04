package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.JedisPool
import java.time.Duration

/** For each command, a Jedis instance is retrieved from the pool and returned once the command has been issued. */
class RealRedis(private val jedisPool: JedisPool) : Redis {
  // Delete key
  override fun del(key: String): Boolean {
    jedisPool.resource.use { jedis ->
      return (jedis.del(key) == 1L)
    }
  }

  // Delete multiple keys
  override fun del(vararg keys: String): Int {
    jedisPool.resource.use { jedis ->
      return jedis.del(*keys).toInt()
    }
  }

  // Get a ByteString value
  override fun get(key: String): ByteString? {
    jedisPool.resource.use { jedis ->
      return jedis.get(key.toByteArray(charset))?.toByteString()
    }
  }

  // Set a ByteArray value
  override fun set(key: String, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.set(key.toByteArray(charset), value.toByteArray())
    }
  }

  // Set a ByteArray value with an expiration
  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.setex(key.toByteArray(charset), expiryDuration.seconds.toInt(), value.toByteArray())
    }
  }

  /** Closes the connection to Redis. */
  fun close() {
    return jedisPool.close()
  }

  companion object {
    /** The charset used to convert String keys to ByteArrays for Jedis commands. */
    private val charset = Charsets.UTF_8
  }
}
