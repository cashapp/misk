package misk.redis

import jakarta.inject.Inject
import okio.ByteString
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.args.ListDirection
import misk.logging.getLogger
import java.time.Duration
import java.util.function.Supplier

internal class TestAlwaysPipelinedRedis @Inject constructor(
  private val unifiedJedis: UnifiedJedis,
) : Redis {

  private fun <T> runPipeline(block: DeferredRedis.() -> Supplier<T>): T {
    return unifiedJedis.pipelined().use { pipeline ->
      block(RealPipelinedRedis(pipeline))
    }.get()
  }

  override fun del(key: String): Boolean = runPipeline { del(key) }

  override fun del(vararg keys: String): Int = runPipeline { del(*keys) }

  override fun mget(vararg keys: String): List<ByteString?> = runPipeline { mget(*keys) }

  override fun mset(vararg keyValues: ByteString) = runPipeline { mset(*keyValues) }

  override fun get(key: String): ByteString? = runPipeline { get(key) }

  override fun getDel(key: String): ByteString? = runPipeline { getDel(key) }

  override fun hdel(key: String, vararg fields: String): Long =
    runPipeline { hdel(key, *fields) }

  override fun hget(key: String, field: String): ByteString? =
    runPipeline { hget(key, field) }

  override fun hgetAll(key: String): Map<String, ByteString>? = runPipeline { hgetAll(key) }

  override fun hlen(key: String): Long = runPipeline { hlen(key) }

  override fun hkeys(key: String): List<ByteString> = runPipeline { hkeys(key) }

  override fun hmget(key: String, vararg fields: String): List<ByteString?> =
    runPipeline { hmget(key, *fields) }

  override fun hincrBy(key: String, field: String, increment: Long): Long =
    runPipeline { hincrBy(key, field, increment) }

  override fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>? =
    runPipeline { hrandFieldWithValues(key, count) }

  override fun hrandField(key: String, count: Long): List<String> =
    runPipeline { hrandField(key, count) }

  override fun scan(cursor: String, matchPattern: String?, count: Int?): Redis.ScanResult =
    error("scan is not supported in TestAlwaysPipelinedRedis")

  override fun set(key: String, value: ByteString) = runPipeline { set(key, value) }

  override fun set(key: String, expiryDuration: Duration, value: ByteString) =
    runPipeline { set(key, value, expiryDuration) }

  override fun setnx(key: String, value: ByteString): Boolean =
    runPipeline { setnx(key, value) }

  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean =
    runPipeline { setnx(key, value, expiryDuration) }

  override fun hset(key: String, field: String, value: ByteString): Long =
    runPipeline { hset(key, field, value) }

  override fun hset(key: String, hash: Map<String, ByteString>): Long =
    runPipeline { hset(key, hash) }

  override fun incr(key: String): Long = runPipeline { incr(key) }

  override fun incrBy(key: String, increment: Long): Long =
    runPipeline { incrBy(key, increment) }

  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString? = runPipeline { blmove(sourceKey, destinationKey, from, to, timeoutSeconds) }

  override fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): ByteString? = runPipeline { brpoplpush(sourceKey, destinationKey, timeoutSeconds) }

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? = runPipeline { lmove(sourceKey, destinationKey, from, to) }

  override fun lpush(key: String, vararg elements: ByteString): Long =
    runPipeline { lpush(key, *elements) }

  override fun rpush(key: String, vararg elements: ByteString): Long =
    runPipeline { rpush(key, *elements) }

  override fun lpop(key: String, count: Int): List<ByteString?> =
    runPipeline { lpop(key, count) }

  override fun lpop(key: String): ByteString? = runPipeline { lpop(key) }

  override fun blpop(keys: Array<String>, timeoutSeconds: Double): Pair<String, ByteString>? =
    runPipeline { blpop(keys, timeoutSeconds) }

  override fun rpop(key: String, count: Int): List<ByteString?> =
    runPipeline { rpop(key, count) }

  override fun llen(key: String): Long = runPipeline { llen(key) }

  override fun rpop(key: String): ByteString? = runPipeline { rpop(key) }

  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> =
    runPipeline { lrange(key, start, stop) }

  override fun ltrim(key: String, start: Long, stop: Long): Unit =
    runPipeline { ltrim(key, start, stop) }

  override fun lrem(key: String, count: Long, element: ByteString): Long =
    runPipeline { lrem(key, count, element) }

  override fun rpoplpush(sourceKey: String, destinationKey: String): ByteString? =
    runPipeline { rpoplpush(sourceKey, destinationKey) }

  override fun exists(key: String): Boolean =
    runPipeline { exists(key)  }

  override fun exists(vararg key: String): Long =
    runPipeline { exists(*key)  }

  override fun persist(key: String): Boolean =
    runPipeline { persist(key) }

  override fun expire(key: String, seconds: Long): Boolean =
    runPipeline { expire(key, seconds) }

  override fun expireAt(key: String, timestampSeconds: Long): Boolean =
    runPipeline { expireAt(key, timestampSeconds) }

  override fun pExpire(key: String, milliseconds: Long): Boolean =
    runPipeline { pExpire(key, milliseconds) }

  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean =
    runPipeline { pExpireAt(key, timestampMilliseconds) }

  override fun watch(vararg keys: String) {
    error("watch is not supported in TestAlwaysPipelinedRedis")
  }

  override fun unwatch(vararg keys: String) {
    error("unwatch is not supported in TestAlwaysPipelinedRedis")
  }

  override fun multi(): Transaction {
    error("multi is not supported in TestAlwaysPipelinedRedis")
  }

  @Deprecated("Use pipelining instead.")
  override fun pipelined(): Pipeline {
    error("pipelined is not supported in TestAlwaysPipelinedRedis")
  }

  override fun pipelining(block: DeferredRedis.() -> Unit): Unit = runPipeline {
    block(this)
    Supplier { }
  }

  override fun close() {
    unifiedJedis.close()
  }

  override fun subscribe(jedisPubSub: JedisPubSub, channel: String) {
    error("subscribe is not supported in TestAlwaysPipelinedRedis")
  }

  override fun publish(channel: String, message: String) {
    error("publish is not supported in TestAlwaysPipelinedRedis")
  }

  override fun flushAll() {
    unifiedJedis.flushAllWithClusterSupport(logger)
  }

  override fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: Redis.ZAddOptions
  ): Long = runPipeline { zadd(key, score, member, *options) }

  override fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: Redis.ZAddOptions
  ): Long = runPipeline { zadd(key, scoreMembers, *options) }

  override fun zscore(key: String, member: String): Double? =
    runPipeline { zscore(key, member) }

  override fun zrange(
    key: String,
    type: Redis.ZRangeType,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean,
    limit: Redis.ZRangeLimit?
  ): List<ByteString?> = runPipeline { zrange(key, type, start, stop, reverse, limit) }

  override fun zrangeWithScores(
    key: String,
    type: Redis.ZRangeType,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean,
    limit: Redis.ZRangeLimit?
  ): List<Pair<ByteString?, Double>> =
    runPipeline { zrangeWithScores(key, type, start, stop, reverse, limit) }

  override fun zremRangeByRank(
    key: String,
    start: Redis.ZRangeRankMarker,
    stop: Redis.ZRangeRankMarker
  ): Long = runPipeline { zremRangeByRank(key, start, stop) }

  override fun zcard(key: String): Long = runPipeline { zcard(key) }

  companion object {
    private val logger = getLogger<TestAlwaysPipelinedRedis>()
  }
}
