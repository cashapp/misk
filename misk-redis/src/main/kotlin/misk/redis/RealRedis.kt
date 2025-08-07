package misk.redis

import misk.redis.Redis.ZAddOptions
import misk.redis.Redis.ZRangeIndexMarker
import misk.redis.Redis.ZRangeLimit
import misk.redis.Redis.ZRangeMarker
import misk.redis.Redis.ZRangeRankMarker
import misk.redis.Redis.ZRangeScoreMarker
import misk.redis.Redis.ZRangeType
import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.commands.JedisBinaryCommands
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.SetParams
import redis.clients.jedis.params.ZRangeParams
import redis.clients.jedis.resps.Tuple
import redis.clients.jedis.util.JedisClusterCRC16
import misk.logging.getLogger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.reflect.cast

/**
 * For each command, a Jedis instance is retrieved from the pool and returned once the command has
 * been issued.
 */
class RealRedis(
  private val unifiedJedis: UnifiedJedis,
  private val clientMetrics: RedisClientMetrics,
) : Redis {
  override fun del(key: String): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { del(keyBytes) == 1L }
  }

  override fun del(vararg keys: String): Int {
    return when (unifiedJedis) {
      is JedisPooled -> {
        val keysAsBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
        jedis { unifiedJedis.del(*keysAsBytes) }.toInt()
      }

      is JedisCluster -> {
        // JedisCluster does not support multi-key del, so we need to group by slot and perform del for each slot
        keys.groupBy { JedisClusterCRC16.getSlot(it) }
          .map { (_, slotKeys) -> jedis { unifiedJedis.del(*slotKeys.toTypedArray()) } }
          .sumOf { it.toInt() }
      }

      else -> throw RuntimeException("Unsupported UnifiedJedis implementation ${unifiedJedis.javaClass}")
    }
  }

  override fun mget(vararg keys: String): List<ByteString?> {
    return when (unifiedJedis) {
      is JedisPooled -> {
        val keysAsBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
        jedis { unifiedJedis.mget(*keysAsBytes) }.map { it?.toByteString() }
      }

      is JedisCluster -> {
        // JedisCluster does not support multi-key mget, so we need to group by slot and perform mget for each slot
        val keyToValueMap = mutableMapOf<String, ByteString?>()
        keys.groupBy { JedisClusterCRC16.getSlot(it) }
          .flatMap { (_, slotKeys) ->
            val result = jedis { unifiedJedis.mget(*slotKeys.toTypedArray()) }
            slotKeys.zip(result)
          }.forEach { (key, value) ->
            keyToValueMap[key] = value?.toByteArray(charset)?.toByteString()
          }
        keys.map { keyToValueMap[it] }
      }

      else -> throw RuntimeException("Unsupported UnifiedJedis implementation ${unifiedJedis.javaClass}")
    }

  }

  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) {
      "Wrong number of arguments to mset (must be a multiple of 2, alternating keys and values)"
    }
    when (unifiedJedis) {
      is JedisPooled -> {
        val byteArrays = keyValues.map { it.toByteArray() }.toTypedArray()
        return jedis { unifiedJedis.mset(*byteArrays) }
      }

      is JedisCluster -> {
        // JedisCluster does not support multi-key mset, so we need to group by slot and perform mset for each slot
        keyValues.toList().chunked(2).groupBy { JedisClusterCRC16.getSlot(it[0].toByteArray()) }
          .forEach { (_, slotKeys) ->
            jedis { unifiedJedis.mset(*slotKeys.flatten().map { it.toByteArray() }.toTypedArray()) }
          }
      }

      else -> throw RuntimeException("Unsupported UnifiedJedis implementation ${unifiedJedis.javaClass}")
    }
  }

  override fun get(key: String): ByteString? {
    val keyBytes = key.toByteArray(charset)
    return jedis { get(keyBytes) }?.toByteString()
  }

  override fun getDel(key: String): ByteString? {
    val keyBytes = key.toByteArray(charset)
    return jedis { getDel(keyBytes) }?.toByteString()
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

  override fun hkeys(key: String): List<ByteString> {
    val keyBytes = key.toByteArray(charset)
    return jedis { hkeys(keyBytes) }
      .map { it.toByteString() }
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
      .associate { it.key.toString(charset) to it.value.toByteString() }
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

  override fun scan(cursor: String, matchPattern: String?, count: Int?): Redis.ScanResult {
    val cursorBytes = cursor.toByteArray(charset)
    val results = jedis {
      if (matchPattern != null || count != null) {
        val params = ScanParams().apply {
          matchPattern?.let { match(it) }
          count?.let { count(it) }
        }
        scan(cursorBytes, params)
      } else {
        scan(cursorBytes)
      }
    }
    val keys = results.result.map {
      it.toString(charset)
    }
    return Redis.ScanResult(results.cursor, keys)
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
    checkSlot("BLMOVE", listOf(sourceKeyBytes, destKeyBytes))
    return jedis { blmove(sourceKeyBytes, destKeyBytes, from, to, timeoutSeconds) }?.toByteString()
  }

  override fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    checkSlot("BRPOPLPUSH", listOf(sourceKeyBytes, destKeyBytes))
    return jedis { brpoplpush(sourceKeyBytes, destKeyBytes, timeoutSeconds) }?.toByteString()
  }

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    checkSlot("LMOVE", listOf(sourceKeyBytes, destKeyBytes))
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

  override fun llen(key: String): Long {
    val keyBytes = key.toByteArray(charset)
    return jedis { llen(keyBytes) }
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

  override fun ltrim(key: String, start: Long, stop: Long) {
    val keyBytes = key.toByteArray(charset)
    jedis { ltrim(keyBytes, start, stop) }
  }

  override fun lrem(key: String, count: Long, element: ByteString): Long {
    val keyBytes = key.toByteArray(charset)
    val elementBytes = element.toByteArray()
    return jedis { lrem(keyBytes, count, elementBytes) }
  }

  override fun rpoplpush(sourceKey: String, destinationKey: String): ByteString? {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    checkSlot("RPOPLPUSH", listOf(sourceKeyBytes, destKeyBytes))
    return jedis { rpoplpush(sourceKeyBytes, destKeyBytes) }?.toByteString()
  }

  override fun persist(key: String): Boolean {
    val keyBytes = key.toByteArray(charset)
    return jedis { persist(keyBytes) == 1L }
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
    invokeTransactionOp { watch(*keysAsBytes) }
  }

  override fun unwatch(vararg keys: String) {
    invokeTransactionOp { unwatch() }
  }

  private fun invokeTransactionOp(op: Transaction.() -> Unit) {
    when (unifiedJedis) {
      is JedisPooled -> unifiedJedis.pool.resource.use { connection ->
        val transaction = Transaction(connection, false)
        transaction.op()
      }

      else -> error("Unsupported UnifiedJedis implementation ${unifiedJedis.javaClass}")
    }
  }

  // Transactions do not get client histogram metrics right now.
  // multi() returns the jedis to the pool, despite returning a Transaction that holds a reference.
  // This is a bug, and will be fixed in a follow-up.
  override fun multi(): Transaction {
    return unifiedJedis.multi() as? Transaction ?: error("Transactions aren't supported in misk-redis with ${unifiedJedis.javaClass} at this time.")
  }

  // Pipelined requests do not get client histogram metrics right now.
  // pipelined() returns the jedis to the pool, despite returning a Pipeline that holds a reference
  // to the borrowed jedis connection.
  // This is a bug, and will be fixed in a follow-up.
  @Deprecated("Use pipelining instead.")
  override fun pipelined(): Pipeline {
    return unifiedJedis.pipelined() as Pipeline
  }

  override fun pipelining(block: DeferredRedis.() -> Unit) {
      unifiedJedis.pipelined().use { pipeline ->
      block(RealPipelinedRedis(pipeline))
    }
  }

  /** Closes the connection to Redis. */
  override fun close() {
    return unifiedJedis.close()
  }

  override fun subscribe(jedisPubSub: JedisPubSub, channel: String) {
    unifiedJedis.subscribe(jedisPubSub, channel)
  }

  override fun publish(channel: String, message: String) {
    unifiedJedis.publish(channel, message)
  }

  override fun flushAll() {
    unifiedJedis.flushAllWithClusterSupport(logger)
  }

  override fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: ZAddOptions,
  ): Long {
    ZAddOptions.verify(options)

    return unifiedJedis.zadd(
      key.toByteArray(charset),
      score,
      member.toByteArray(charset),
      ZAddOptions.getZAddParams(options)
    )
  }

  override fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: ZAddOptions,
  ): Long {
    val params = ZAddOptions.getZAddParams(options)
    val keyBytes = key.toByteArray(charset)
    val scoreMembersBytes =
      scoreMembers.entries.associate { it.key.toByteArray(charset) to it.value }
    return unifiedJedis.zadd(
      keyBytes,
      scoreMembersBytes,
      params
    )
  }

  override fun zscore(key: String, member: String): Double? {
    return unifiedJedis.zscore(
      key.toByteArray(charset),
      member.toByteArray(charset)
    )
  }

  override fun zrange(
    key: String,
    type: ZRangeType,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean,
    limit: ZRangeLimit?,
  ): List<ByteString?> {
    return zrangeBase(key, type, start, stop, reverse, false, limit)
      .noScore?.map { it?.toByteString() } ?: listOf()
  }

  override fun zrangeWithScores(
    key: String,
    type: ZRangeType,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean,
    limit: ZRangeLimit?,
  ): List<Pair<ByteString?, Double>> {
    return zrangeBase(key, type, start, stop, reverse, true, limit)
      .withScore?.map { Pair(it.binaryElement?.toByteString(), it.score) } ?: listOf()
  }

  override fun zremRangeByRank(
    key: String,
    start: ZRangeRankMarker,
    stop: ZRangeRankMarker
  ): Long {
    return unifiedJedis.zremrangeByRank(key, start.longValue, stop.longValue)
  }

  override fun zcard(
    key: String
  ): Long {
    return unifiedJedis.zcard(key)
  }

  private fun zrangeBase(
    key: String,
    type: ZRangeType,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean,
    withScore: Boolean,
    limit: ZRangeLimit?,
  ): ZRangeResponse {
    return when (type) {
      ZRangeType.INDEX ->
        zrangeByIndex(
          key, start as ZRangeIndexMarker, stop as ZRangeIndexMarker, reverse,
          withScore
        )

      ZRangeType.SCORE ->
        zrangeByScore(
          key, start as ZRangeScoreMarker, stop as ZRangeScoreMarker, reverse,
          withScore, limit
        )
    }
  }

  private fun zrangeByScore(
    key: String,
    start: ZRangeScoreMarker,
    stop: ZRangeScoreMarker,
    reverse: Boolean,
    withScore: Boolean,
    limit: ZRangeLimit?,
  ): ZRangeResponse {
    val minString = start.toString()
    val maxString = stop.toString()

    return if (limit == null && !reverse && !withScore) {
      ZRangeResponse.noScore(
        unifiedJedis.zrangeByScore(
          key.toByteArray(charset),
          minString.toByteArray(charset),
          maxString.toByteArray(charset)
        )
      )
    } else if (limit == null && !reverse) {
      ZRangeResponse.withScore(
        unifiedJedis.zrangeByScoreWithScores(
          key.toByteArray(charset),
          minString.toByteArray(charset),
          maxString.toByteArray(charset)
        )
      )
    } else if (limit == null && !withScore) {
      ZRangeResponse.noScore(
        unifiedJedis.zrevrangeByScore(
          key.toByteArray(charset),
          maxString.toByteArray(charset),
          minString.toByteArray(charset)
        )
      )
    } else if (limit == null) {
      ZRangeResponse.withScore(
        unifiedJedis.zrevrangeByScoreWithScores(
          key.toByteArray(charset),
          maxString.toByteArray(charset),
          minString.toByteArray(charset)
        )
      )
    } else if (!reverse && !withScore) {
      ZRangeResponse.noScore(
        unifiedJedis.zrangeByScore(
          key.toByteArray(charset),
          minString.toByteArray(charset),
          maxString.toByteArray(charset),
          limit.offset,
          limit.count
        )
      )
    } else if (!reverse) {
      ZRangeResponse.withScore(
        unifiedJedis.zrangeByScoreWithScores(
          key.toByteArray(charset),
          minString.toByteArray(charset),
          maxString.toByteArray(charset),
          limit.offset,
          limit.count
        )
      )
    } else if (!withScore) {
      ZRangeResponse.noScore(
        unifiedJedis.zrevrangeByScore(
          key.toByteArray(charset),
          maxString.toByteArray(charset),
          minString.toByteArray(charset),
          limit.offset,
          limit.count
        )
      )
    } else {
      ZRangeResponse.withScore(
        unifiedJedis.zrevrangeByScoreWithScores(
          key.toByteArray(charset),
          maxString.toByteArray(charset),
          minString.toByteArray(charset),
          limit.offset,
          limit.count
        )
      )
    }
  }

  /**
   * A wrapper class for handling response from zrange* methods.
   */
  private class ZRangeResponse private constructor(
    val noScore: List<ByteArray?>?,
    val withScore: List<Tuple>?
  ) {
    companion object {
      fun noScore(ans: List<ByteArray?>): ZRangeResponse = ZRangeResponse(ans, null)

      fun withScore(ans: List<Tuple>): ZRangeResponse = ZRangeResponse(null, ans)
    }
  }

  private fun zrangeByIndex(
    key: String,
    start: ZRangeIndexMarker,
    stop: ZRangeIndexMarker,
    reverse: Boolean,
    withScore: Boolean
  ): ZRangeResponse {
    val params = ZRangeParams(
      start.intValue,
      stop.intValue
    )
    if (reverse) params.rev()

    return if (withScore) {
      ZRangeResponse.withScore(unifiedJedis.zrangeWithScores(key.toByteArray(charset), params))
    } else {
      ZRangeResponse.noScore(unifiedJedis.zrange(key.toByteArray(charset), params))
    }
  }

  // Gets a Jedis instance from the pool, and times the requested method invocations.
  private fun <T> jedis(op: JedisBinaryCommands.() -> T): T {
    updateMetrics()
    val invocationHandler = JedisTimedInvocationHandler(unifiedJedis, clientMetrics)
    val timedProxy = JedisBinaryCommands::class.cast(
      Proxy.newProxyInstance(
        ClassLoader.getSystemClassLoader(),
        arrayOf(JedisBinaryCommands::class.java),
        invocationHandler
      )
    )
    val response = timedProxy.op()
    updateMetrics()
    return response
  }

  private fun updateMetrics() {
    when (unifiedJedis) {
      is JedisPooled -> clientMetrics.setActiveIdleConnectionMetrics(unifiedJedis.pool)
    }
  }

  private class JedisTimedInvocationHandler(
    private val jedisCommand: JedisBinaryCommands,
    private val clientMetrics: RedisClientMetrics,
  ) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? =
      try {
        clientMetrics.timed(method.name) {
          method.invoke(jedisCommand, *(args ?: arrayOf()))
        }
      } catch (e: InvocationTargetException) {
        throw e.cause!!
      }
  }

  private fun checkSlot(op: String, keys: List<ByteArray>) {
    if (unifiedJedis !is JedisCluster) {
      return
    }
    val error = getSlotErrorOrNull(op, keys) ?: return
    throw error
  }

  companion object {
    val charset = charset("UTF-8")

    private val logger = getLogger<RealRedis>()
  }
}
