package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import java.time.Duration

/** For each command, a Jedis instance is retrieved from the pool and returned once the command has been issued. */
class RealRedis(private val jedisPool: JedisPool) : Redis {
  /** Delete key. */
  override fun del(key: String): Boolean {
    jedisPool.resource.use { jedis ->
      return (jedis.del(key) == 1L)
    }
  }

  /** Delete multiple keys. */
  override fun del(vararg keys: String): Int {
    jedisPool.resource.use { jedis ->
      return jedis.del(*keys).toInt()
    }
  }

  /** Get multiple key values. */
  override fun mget(vararg keys: String): List<ByteString?> {
    val byteArrays = keys.map { it.toByteArray(charset) }.toTypedArray()
    jedisPool.resource.use { jedis ->
      return jedis.mget(*byteArrays).map { it?.toByteString() }
    }
  }

  /** Set multiple key values. */
  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

    val byteArrays = keyValues.map { it.toByteArray() }.toTypedArray()
    jedisPool.resource.use { jedis ->
      jedis.mset(*byteArrays)
    }
  }

  /** Get a ByteString value. */
  override fun get(key: String): ByteString? {
    jedisPool.resource.use { jedis ->
      return jedis.get(key.toByteArray(charset))?.toByteString()
    }
  }

  /** Get a map of field -> value pairs for the given key. */
  override fun hgetAll(key: String): Map<String, ByteString>? {
    jedisPool.resource.use { jedis ->
      return jedis.hgetAll(key.toByteArray(charset))?.mapKeys {
        it.key.toString(charset)
      }?.mapValues {
        it.value.toByteString()
      }
    }
  }

  /** Get a ByteString value for the given key and field. */
  override fun hget(key: String, field: String): ByteString? {
    jedisPool.resource.use { jedis ->
      return jedis.hget(key.toByteArray(charset), field.toByteArray(charset))?.toByteString()
    }
  }

  override fun hincrBy(key: String, field: String, increment: Long): Long {
    jedisPool.resource.use { jedis ->
      return jedis.hincrBy(key, field, increment)
    }
  }

  /** Set a ByteArray value. */
  override fun set(key: String, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.set(key.toByteArray(charset), value.toByteArray())
    }
  }

  /** Set a ByteArray value with an expiration. */
  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.setex(key.toByteArray(charset), expiryDuration.seconds.toInt(), value.toByteArray())
    }
  }

  /** Set a ByteArray value if it doesn't already exist. */
  override fun setnx(key: String, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.setnx(key.toByteArray(charset), value.toByteArray())
    }
  }

  /** Set a ByteArray value if it doesn't already exist with an expiration. */
  override fun setnx(key: String, expiryDuration: Duration, value: ByteString) {
    val setParams = SetParams.setParams().ex(expiryDuration.seconds.toInt()).nx()
    jedisPool.resource.use { jedis ->
      jedis.set(key.toByteArray(charset), value.toByteArray(), setParams)
    }
  }

  /** Set a ByteArray value for the given key and field. */
  override fun hset(key: String, field: String, value: ByteString) {
    jedisPool.resource.use { jedis ->
      jedis.hset(key.toByteArray(charset), field.toByteArray(charset), value.toByteArray())
    }
  }

  /** Interpret the value at [key] as a Long and increment it by 1. */
  override fun incr(key: String): Long {
    return jedisPool.resource.use { jedis ->
      jedis.incr(key.toByteArray(charset))!!
    }
  }

  /** Interpret the value at [key] as a Long and increment it by [increment]. */
  override fun incrBy(key: String, increment: Long): Long {
    return jedisPool.resource.use { jedis ->
      jedis.incrBy(key.toByteArray(charset), increment)!!
    }
  }

  override fun expire(key: String, seconds: Long): Boolean {
    return jedisPool.resource.use { jedis ->
      jedis.expire(key, seconds)!! == 1L
    }
  }

  override fun expireAt(key: String, timestampSeconds: Long): Boolean {
    return jedisPool.resource.use { jedis ->
      jedis.expireAt(key, timestampSeconds)!! == 1L
    }
  }

  override fun pExpire(key: String, milliseconds: Long): Boolean {
    return jedisPool.resource.use { jedis ->
      jedis.pexpire(key, milliseconds)!! == 1L
    }
  }

  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean {
    return jedisPool.resource.use { jedis ->
      jedis.pexpireAt(key, timestampMilliseconds)!! == 1L
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
