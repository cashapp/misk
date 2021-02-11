package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
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

  // Get multiple key values
  override fun mget(vararg keys: String): List<ByteString?> {
    val byteArrays = keys.map { it.toByteArray(charset) }.toTypedArray()
    jedisPool.resource.use { jedis ->
      return jedis.mget(*byteArrays).map { it?.toByteString() }
    }
  }

  // Set multiple key values
  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

    val byteArrays = keyValues.map { it.toByteArray() }.toTypedArray()
    jedisPool.resource.use { jedis ->
      jedis.mset(*byteArrays)
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

  // Set a ByteArray value if it doesn't already exist
  override fun setnx(key: String, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.setnx(key.toByteArray(charset), value.toByteArray())
    }
  }

  // Set a ByteArray value if it doesn't already exist with an expiration
  override fun setnx(key: String, expiryDuration: Duration, value: ByteString) {
    val setParams = SetParams.setParams().ex(expiryDuration.seconds.toInt()).nx()
    jedisPool.resource.use { jedis ->
      jedis.set(key.toByteArray(charset), value.toByteArray(), setParams)
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
