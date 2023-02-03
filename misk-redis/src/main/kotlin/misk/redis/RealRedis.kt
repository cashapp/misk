package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection
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

  override fun hdel(key: String, vararg fields: String): Long {
    jedisPool.resource.use { jedis ->
      val fieldsAsByteArrays = fields.map { it.toByteArray(charset) }.toTypedArray()
      return jedis.hdel(key.toByteArray(charset), *fieldsAsByteArrays)
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

  override fun hmget(key: String, vararg fields: String): List<ByteString?> {
    jedisPool.resource.use { jedis ->
      val fieldsAsByteArrays = fields.map { it.toByteArray(charset) }.toTypedArray()
      return jedis.hmget(key.toByteArray(charset), *fieldsAsByteArrays).map { it?.toByteString() }
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

  /**
   * Throws if [count] is negative.
   *
   * See [misk.redis.Redis.hrandFieldWithValues].
   */
  override fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>? {
    checkHrandFieldCount(count)
    return jedisPool.resource.use { jedis ->
      jedis.hrandfieldWithValues(key.toByteArray(charset), count)
        ?.mapKeys { (key, _) -> key.toString(charset) }
        ?.mapValues { (_, value) -> value.toByteString() }
    }
  }

  /**
   * Throws if [count] is negative.
   *
   * See [misk.redis.Redis.hrandField].
   */
  override fun hrandField(key: String, count: Long): List<String> {
    checkHrandFieldCount(count)
    return jedisPool.resource.use { jedis ->
      jedis.hrandfield(key.toByteArray(charset), count)
        .map { it.toString(charset) }
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
      jedis.setex(key.toByteArray(charset), expiryDuration.seconds, value.toByteArray())
    }
  }

  /** Set a ByteArray value if it doesn't already exist. Returns true if set and false, otherwise */
  override fun setnx(key: String, value: ByteString): Boolean {
    return jedisPool.resource.use { jedis ->
      jedis.setnx(key.toByteArray(charset), value.toByteArray()) == 1L
    }
  }

  /** Set a ByteArray value if it doesn't already exist with an expiration. Returns true if set and false, otherwise  */
  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean {
    return jedisPool.resource.use { jedis ->
      jedis.set(
        key.toByteArray(charset),
        value.toByteArray(),
        SetParams().nx().px(expiryDuration.toMillis())
      ) == "OK"
    }
  }

  /** Set a ByteArray value for the given key and field. */
  override fun hset(key: String, field: String, value: ByteString): Long {
    return jedisPool.resource.use { jedis ->
      jedis.hset(key.toByteArray(charset), field.toByteArray(charset), value.toByteArray())
    }
  }

  /** Set ByteArray values for the given key and fields. */
  override fun hset(key: String, hash: Map<String, ByteString>): Long {
    val hashBytes = hash.entries.associate { it.key.toByteArray(charset) to it.value.toByteArray() }
    return jedisPool.resource.use { jedis ->
      jedis.hset(key.toByteArray(charset), hashBytes)
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

  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString? {
    return jedisPool.resource.use { jedis ->
      jedis.blmove(
        sourceKey.toByteArray(charset),
        destinationKey.toByteArray(charset),
        from,
        to,
        timeoutSeconds
      )?.toByteString()
    }
  }

  override fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): ByteString? {
    return jedisPool.resource.use { jedis ->
      jedis.brpoplpush(
        sourceKey.toByteArray(charset),
        destinationKey.toByteArray(charset),
        timeoutSeconds,
      )?.toByteString()
    }
  }

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? {
    return jedisPool.resource.use { jedis ->
      jedis.lmove(
        sourceKey.toByteArray(charset),
        destinationKey.toByteArray(charset),
        from,
        to
      ).toByteString()
    }
  }

  override fun lpush(key: String, vararg elements: ByteString): Long {
    return jedisPool.resource.use { jedis ->
      jedis.lpush(key.toByteArray(charset), *elements.map { it.toByteArray() }.toTypedArray())
    }
  }

  override fun rpush(key: String, vararg elements: ByteString): Long {
    return jedisPool.resource.use { jedis ->
      jedis.rpush(key.toByteArray(charset), *elements.map { it.toByteArray() }.toTypedArray())
    }
  }

  override fun lpop(key: String, count: Int): List<ByteString?> {
    return jedisPool.resource.use { jedis ->
      jedis.lpop(key.toByteArray(charset), count).map { it?.toByteString() }
    }
  }

  override fun rpop(key: String, count: Int): List<ByteString?> {
    return jedisPool.resource.use { jedis ->
      jedis.rpop(key.toByteArray(charset), count).map { it?.toByteString() }
    }
  }

  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> {
    return jedisPool.resource.use { jedis ->
      jedis.lrange(key.toByteArray(charset), start, stop).map { it?.toByteString() }
    }
  }

  override fun lrem(key: String, count: Long, element: ByteString): Long {
    return jedisPool.resource.use { jedis ->
      jedis.lrem(key.toByteArray(charset), count, element.toByteArray())
    }
  }

  override fun rpoplpush(sourceKey: String, destinationKey: String): ByteString? {
    return jedisPool.resource.use { jedis ->
      jedis.rpoplpush(sourceKey.toByteArray(charset), destinationKey.toByteArray(charset))
        ?.toByteString()
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

  override fun watch(vararg keys: String) {
    jedisPool.resource.use { jedis ->
      jedis.watch(*keys)
    }
  }

  override fun unwatch(vararg keys: String) {
    jedisPool.resource.use { jedis ->
      jedis.unwatch()
    }
  }

  override fun multi(): Transaction {
    jedisPool.resource.use { jedis ->
      return jedis.multi()
    }
  }

  override fun pipelined(): Pipeline {
    jedisPool.resource.use { jedis ->
      return jedis.pipelined()
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
