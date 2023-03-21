package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.commands.JedisBinaryCommands
import redis.clients.jedis.params.SetParams
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.reflect.cast

/**
 * For each command, a Jedis instance is retrieved from the pool and returned once the command has
 * been issued.
 */
class RealRedis(
  private val jedisPool: JedisPool,
  private val clientMetrics: RedisClientMetrics,
) : Redis {
  override fun del(key: String): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { del(keyBytes) == 1L }
  }

  override fun del(vararg keys: String): Int {
    val keysAsBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
    return jedis { del(*keysAsBytes) }.toInt()
  }

  override fun mget(vararg keys: String): List<ByteString?> {
    val keysAsBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
    return jedis { mget(*keysAsBytes) }
      .map { it?.toByteString() }
  }

  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }
    val byteArrays = keyValues.map { it.toByteArray() }.toTypedArray()
    jedis { mset(*byteArrays) }
  }

  override fun get(key: String): ByteString? {
    val keyBytes = key.toByteArray(charset)
    return jedis { get(keyBytes) }?.toByteString()
  }

  override fun hdel(key: String, vararg fields: String): Long {
    val fieldsAsByteArrays = fields.map { it.toByteArray(charset) }.toTypedArray()
    val keyBytes = key.toByteArray(charset)
    return jedis { hdel(keyBytes, *fieldsAsByteArrays) }
  }

  override fun hgetAll(key: String): Map<String, ByteString>? {
    val keyBytes = key.toByteArray(charset)
    return jedis { hgetAll(keyBytes) }
      ?.mapKeys { it.key.toString(charset) }
      ?.mapValues { it.value.toByteString() }
  }

  override fun hlen(key: String): Long {
    val keyBytes = key.toByteArray(charset)
    return jedis { hlen(keyBytes) }
  }

  override fun hmget(key: String, vararg fields: String): List<ByteString?> {
    val fieldsAsByteArrays = fields.map { it.toByteArray(charset) }.toTypedArray()
    val keyBytes = key.toByteArray(charset)
    return jedis { hmget(keyBytes, *fieldsAsByteArrays) ?: emptyList() }
      .map { it?.toByteString() }
  }

  override fun hget(key: String, field: String): ByteString? {
    val keyBytes = key.toByteArray(charset)
    val fieldBytes = field.toByteArray(charset)
    return jedis { hget(keyBytes, fieldBytes) }?.toByteString()
  }

  override fun hincrBy(key: String, field: String, increment: Long): Long {
    val keyBytes = key.toByteArray(charset)
    val fieldBytes = field.toByteArray(charset)
    return jedis { hincrBy(keyBytes, fieldBytes, increment) }
  }

  /**
   * Throws if [count] is negative.
   *
   * See [misk.redis.Redis.hrandFieldWithValues].
   */
  override fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>? {
    checkHrandFieldCount(count)
    val keyBytes = key.toByteArray(charset)
    return jedis { hrandfieldWithValues(keyBytes, count) }
      ?.mapKeys { (key, _) -> key.toString(charset) }
      ?.mapValues { (_, value) -> value.toByteString() }
  }

  /**
   * Throws if [count] is negative.
   *
   * See [misk.redis.Redis.hrandField].
   */
  override fun hrandField(key: String, count: Long): List<String> {
    checkHrandFieldCount(count)
    val keyBytes = key.toByteArray(charset)
    return jedis { hrandfield(keyBytes, count) }
      .map { it.toString(charset) }
  }

  override fun set(key: String, value: ByteString) {
    val keyBytes = key.toByteArray(charset)
    val valueBytes = value.toByteArray()
    jedis { set(keyBytes, valueBytes) }
  }

  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    val keyBytes = key.toByteArray(charset)
    val valueBytes = value.toByteArray()
    jedis { setex(keyBytes, expiryDuration.seconds, valueBytes) }
  }

  override fun setnx(key: String, value: ByteString): Boolean {
    val keyBytes = key.toByteArray(charset)
    val valueBytes = value.toByteArray()
    return jedis { setnx(keyBytes, valueBytes) == 1L }
  }

  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean {
    val keyBytes = key.toByteArray(charset)
    val valueBytes = value.toByteArray()
    val params = SetParams().nx().px(expiryDuration.toMillis())
    return jedis { set(keyBytes, valueBytes, params) == "OK" }
  }

  override fun hset(key: String, field: String, value: ByteString): Long {
    val keyBytes = key.toByteArray(charset)
    val fieldBytes = field.toByteArray(charset)
    val valueBytes = value.toByteArray()
    return jedis { hset(keyBytes, fieldBytes, valueBytes) }
  }

  override fun hset(key: String, hash: Map<String, ByteString>): Long {
    val hashBytes = hash.entries.associate { it.key.toByteArray(charset) to it.value.toByteArray() }
    val keyBytes = key.toByteArray(charset)
    return jedis { hset(keyBytes, hashBytes) }
  }

  override fun incr(key: String): Long {
    val keyBytes = key.toByteArray(charset)
    return jedis { incr(keyBytes) }
  }

  override fun incrBy(key: String, increment: Long): Long {
    val keyBytes = key.toByteArray(charset)
    return jedis { incrBy(keyBytes, increment) }
  }

  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    return jedis { blmove(sourceKeyBytes, destKeyBytes, from, to, timeoutSeconds) }?.toByteString()
  }

  override fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destinationKeyBytes = destinationKey.toByteArray(charset)
    return jedis { brpoplpush(sourceKeyBytes, destinationKeyBytes, timeoutSeconds) }?.toByteString()
  }

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    return jedis { lmove(sourceKeyBytes, destKeyBytes, from, to) }?.toByteString()
  }

  override fun lpush(key: String, vararg elements: ByteString): Long {
    val keyBytes = key.toByteArray(charset)
    val byteArrays = elements.map { it.toByteArray() }.toTypedArray()
    return jedis { lpush(keyBytes, *byteArrays) }
  }

  override fun rpush(key: String, vararg elements: ByteString): Long {
    val keyBytes = key.toByteArray(charset)
    val byteArrays = elements.map { it.toByteArray() }.toTypedArray()
    return jedis { rpush(keyBytes, *byteArrays) }
  }

  override fun lpop(key: String, count: Int): List<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    return jedis { lpop(keyBytes, count) ?: emptyList() }
      .map { it?.toByteString() }
  }

  override fun lpop(key: String): ByteString? {
    val keyBytes = key.toByteArray(charset)
    return jedis { lpop(keyBytes) }?.toByteString()
  }

  override fun rpop(key: String, count: Int): List<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    return jedis { rpop(keyBytes, count) ?: emptyList() }
      .map { it?.toByteString() }
  }

  override fun rpop(key: String): ByteString? {
    val keyBytes = key.toByteArray(charset)
    return jedis { rpop(keyBytes) }?.toByteString()
  }

  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    return jedis { lrange(keyBytes, start, stop) ?: emptyList() }
      .map { it?.toByteString() }
  }

  override fun lrem(key: String, count: Long, element: ByteString): Long {
    val keyBytes = key.toByteArray(charset)
    val elementBytes = element.toByteArray()
    return jedis { lrem(keyBytes, count, elementBytes) }
  }

  override fun rpoplpush(sourceKey: String, destinationKey: String): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destinationKeyBytes = destinationKey.toByteArray(charset)
    return jedis { rpoplpush(sourceKeyBytes, destinationKeyBytes) }?.toByteString()
  }

  override fun expire(key: String, seconds: Long): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { expire(keyBytes, seconds) == 1L }
  }

  override fun expireAt(key: String, timestampSeconds: Long): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { expireAt(keyBytes, timestampSeconds) == 1L }
  }

  override fun pExpire(key: String, milliseconds: Long): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { pexpire(keyBytes, milliseconds) == 1L }
  }

  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { pexpireAt(keyBytes, timestampMilliseconds) == 1L }
  }

  override fun watch(vararg keys: String) {
    val keysAsBytes = keys.map { it.toByteArray() }.toTypedArray()
    jedisPool.resource.use { jedis -> jedis.watch(*keysAsBytes) }
  }

  override fun unwatch(vararg keys: String) {
    jedisPool.resource.use { jedis -> jedis.unwatch() }
  }

  override fun multi(): Transaction = jedisPool.resource.use { multi() }

  override fun pipelined(): Pipeline = jedisPool.resource.use { pipelined() }


  /** Closes the connection to Redis. */
  override fun close() {
    return jedisPool.close()
  }

  private fun <T> jedis(op: JedisBinaryCommands.() -> T): T {
    return jedisPool.resource.use { jedisResource ->
      val invocationHandler = JedisTimedInvocationHandler(jedisResource, clientMetrics)
      val timedProxy = JedisBinaryCommands::class.cast(
        Proxy.newProxyInstance(
          ClassLoader.getSystemClassLoader(),
          arrayOf(JedisBinaryCommands::class.java),
          invocationHandler
        )
      )
      timedProxy.op()
    }
  }

  private class JedisTimedInvocationHandler(
    private val jedis: JedisBinaryCommands,
    private val clientMetrics: RedisClientMetrics,
  ) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any =
      clientMetrics.timed(method.name) {
        method.invoke(jedis, *args ?: arrayOf())
      }
  }

  companion object {
    /** The charset used to convert String keys to ByteArrays for Jedis commands. */
    private val charset = Charsets.UTF_8
  }
}
