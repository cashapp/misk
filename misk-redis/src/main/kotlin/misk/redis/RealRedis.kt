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
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.commands.JedisBinaryCommands
import redis.clients.jedis.params.ZRangeParams
import redis.clients.jedis.resps.Tuple
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.reflect.cast

/**
 * A Redis client implementation with metrics. Supports pooled connections and clustered Redis.
 *
 * Install this to your service using the [RedisModule], and configure it with a [RedisConfig].
 *
 * Note: To keep the implementation simple, this client always defers to [RealPipelinedRedis],
 * even if there is only one command.
 *
 * If you have to issue multiple commands in a row, use [pipelining] to batch them together.
 */
class RealRedis(
  private val unifiedJedis: UnifiedJedis,
  private val clientMetrics: RedisClientMetrics,
) : Redis {
  override fun del(key: String): Boolean = withMetrics("del") {
    runPipeline { del(key) }.get()
  }

  override fun del(vararg keys: String): Int = withMetrics("del") {
    runPipeline { del(*keys) }.get()
  }

  override fun mget(vararg keys: String): List<ByteString?> = withMetrics("mget") {
    runPipeline { mget(*keys) }.get()
  }

  override fun mset(vararg keyValues: ByteString) = withMetrics("mset") {
    runPipeline { mset(*keyValues) }.get()
  }

  override fun get(key: String): ByteString? = withMetrics("get") {
    runPipeline { get(key) }.get()
  }

  override fun getDel(key: String): ByteString? = withMetrics("getDel") {
    runPipeline { getDel(key) }.get()
  }

  override fun hdel(key: String, vararg fields: String): Long = withMetrics("hdel") {
    runPipeline { hdel(key, *fields) }.get()
  }

  override fun hgetAll(key: String): Map<String, ByteString>? = withMetrics("hgetAll") {
    runPipeline { hgetAll(key) }.get()
  }

  override fun hlen(key: String): Long = withMetrics("hlen") {
    runPipeline { hlen(key) }.get()
  }

  override fun hmget(key: String, vararg fields: String): List<ByteString?> = withMetrics("hmget") {
    runPipeline { hmget(key, *fields) }.get()
  }

  override fun hget(key: String, field: String): ByteString? = withMetrics("hget") {
    runPipeline { hget(key, field) }.get()
  }

  override fun hincrBy(key: String, field: String, increment: Long): Long = withMetrics("hincrBy") {
    runPipeline { hincrBy(key, field, increment) }.get()
  }

  /**
   * Throws if [count] is negative.
   *
   * See [misk.redis.Redis.hrandFieldWithValues].
   */
  override fun hrandFieldWithValues(
    key: String,
    count: Long
  ): Map<String, ByteString>? = withMetrics("hrandFieldWithValues") {
    runPipeline { hrandFieldWithValues(key, count) }.get()
  }

  /**
   * Throws if [count] is negative.
   *
   * See [misk.redis.Redis.hrandField].
   */
  override fun hrandField(key: String, count: Long): List<String> = withMetrics("hrandField") {
    runPipeline { hrandField(key, count) }.get()
  }

  override fun set(key: String, value: ByteString) = withMetrics("set") {
    runPipeline { set(key, value) }.get()
  }

  override fun set(key: String, expiryDuration: Duration, value: ByteString) = withMetrics("set") {
    runPipeline { set(key, value, expiryDuration) }.get()
  }

  override fun setnx(key: String, value: ByteString): Boolean = withMetrics("setnx") {
    runPipeline { setnx(key, value) }.get()
  }

  override fun setnx(
    key: String,
    expiryDuration: Duration,
    value: ByteString
  ): Boolean = withMetrics("setnx") {
    runPipeline { setnx(key, value, expiryDuration) }.get()
  }

  override fun hset(key: String, field: String, value: ByteString): Long = withMetrics("hset") {
    runPipeline { hset(key, field, value) }.get()
  }

  override fun hset(key: String, hash: Map<String, ByteString>): Long = withMetrics("hset") {
    runPipeline { hset(key, hash) }.get()
  }

  override fun incr(key: String): Long = withMetrics("incr") {
    runPipeline { incr(key) }.get()
  }

  override fun incrBy(key: String, increment: Long): Long = withMetrics("incrBy") {
    runPipeline { incrBy(key, increment) }.get()
  }

  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString? = withMetrics("blmove") {
    runPipeline { blmove(sourceKey, destinationKey, from, to, timeoutSeconds) }.get()
  }

  override fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): ByteString? = withMetrics("brpoplpush") {
    runPipeline { brpoplpush(sourceKey, destinationKey, timeoutSeconds) }.get()
  }

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? = withMetrics("lmove") {
    runPipeline { lmove(sourceKey, destinationKey, from, to) }.get()
  }

  override fun lpush(key: String, vararg elements: ByteString): Long = withMetrics("lpush") {
    runPipeline { lpush(key, *elements) }.get()
  }

  override fun rpush(key: String, vararg elements: ByteString): Long = withMetrics("rpush") {
    runPipeline { rpush(key, *elements) }.get()
  }

  override fun lpop(key: String, count: Int): List<ByteString?> = withMetrics("lpop") {
    runPipeline { lpop(key, count) }.get()
  }

  override fun lpop(key: String): ByteString? = withMetrics("lpop") {
    runPipeline { lpop(key) }.get()
  }

  override fun rpop(key: String, count: Int): List<ByteString?> = withMetrics("rpop") {
    runPipeline { rpop(key, count) }.get()
  }

  override fun rpop(key: String): ByteString? = withMetrics("rpop") {
    runPipeline { rpop(key) }.get()
  }

  override fun lrange(
    key: String,
    start: Long,
    stop: Long
  ): List<ByteString?> = withMetrics("lrange") {
    runPipeline { lrange(key, start, stop) }.get()
  }

  override fun lrem(key: String, count: Long, element: ByteString): Long = withMetrics("lrem") {
    runPipeline { lrem(key, count, element) }.get()
  }

  override fun rpoplpush(
    sourceKey: String,
    destinationKey: String
  ): ByteString? = withMetrics("rpoplpush") {
    runPipeline { rpoplpush(sourceKey, destinationKey) }.get()
  }

  override fun expire(key: String, seconds: Long): Boolean = withMetrics("expire") {
    runPipeline { expire(key, seconds) }.get()
  }

  override fun expireAt(key: String, timestampSeconds: Long): Boolean = withMetrics("expireAt") {
    runPipeline { expireAt(key, timestampSeconds) }.get()
  }

  override fun pExpire(key: String, milliseconds: Long): Boolean = withMetrics("pExpire") {
    runPipeline { pExpire(key, milliseconds) }.get()
  }

  override fun pExpireAt(
    key: String,
    timestampMilliseconds: Long
  ): Boolean = withMetrics("pExpireAt") {
    runPipeline { pExpireAt(key, timestampMilliseconds) }.get()
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
  override fun multi(): Transaction {
    return unifiedJedis.multi() as? Transaction ?: error("Transactions aren't supported in misk-redis with ${unifiedJedis.javaClass} at this time.")
  }

  @Deprecated("Use pipelining instead.")
  override fun pipelined(): Pipeline {
    return unifiedJedis.pipelined() as? Pipeline ?: error("pipelined() isn't supported in misk-redis with ${unifiedJedis.javaClass}. Use pipelining instead.")
  }

  private fun <T> runPipeline(block: DeferredRedis.() -> T): T = withMetrics("pipelining") {
    unifiedJedis.pipelined().use { pipeline ->
      block(RealPipelinedRedis(pipeline))
    }
  }

  override fun pipelining(block: DeferredRedis.() -> Unit) {
    runPipeline(block)
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
    unifiedJedis.flushAll()
  }

  private fun <T> withMetrics(commandName: String, op: () -> T): T {
    updateMetrics()
    return clientMetrics.timed(commandName, op)
      .also { updateMetrics() }
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

  companion object {
    val charset = charset("UTF-8")
  }
}
