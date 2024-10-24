package misk.redis

import misk.redis.Redis.ScanResult
import okio.ByteString
import redis.clients.jedis.args.ListDirection
import java.time.Duration
import java.util.function.Supplier

/**
 * Like [Redis], but returns [Supplier]s to defer value retrieval.
 * **Does not support transactions or pubsub.**
 */
interface DeferredRedis {
  fun del(key: String): Supplier<Boolean>

  fun del(vararg keys: String): Supplier<Int>

  fun mget(vararg keys: String): Supplier<List<ByteString?>>

  fun mset(vararg keyValues: ByteString): Supplier<Unit>

  operator fun get(key: String): Supplier<ByteString?>

  fun getDel(key: String): Supplier<ByteString?>

  fun hdel(key: String, vararg fields: String): Supplier<Long>

  fun hget(key: String, field: String): Supplier<ByteString?>

  fun hgetAll(key: String): Supplier<Map<String, ByteString>?>

  fun hlen(key: String): Supplier<Long>

  fun hmget(key: String, vararg fields: String): Supplier<List<ByteString?>>

  fun hincrBy(key: String, field: String, increment: Long): Supplier<Long>

  fun hrandFieldWithValues(key: String, count: Long): Supplier<Map<String, ByteString>?>

  fun hrandField(key: String, count: Long): Supplier<List<String>>

  fun set(key: String, value: ByteString, expiryDuration: Duration? = null): Supplier<Unit>

  fun setnx(key: String, value: ByteString, expiryDuration: Duration? = null): Supplier<Boolean>

  fun hset(key: String, field: String, value: ByteString): Supplier<Long>

  fun hset(key: String, hash: Map<String, ByteString>): Supplier<Long>

  fun incr(key: String): Supplier<Long>

  fun incrBy(key: String, increment: Long): Supplier<Long>

  fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): Supplier<ByteString?>

  fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): Supplier<ByteString?>

  fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): Supplier<ByteString?>

  fun lpush(key: String, vararg elements: ByteString): Supplier<Long>

  fun rpush(key: String, vararg elements: ByteString): Supplier<Long>

  fun lpop(key: String, count: Int): Supplier<List<ByteString?>>

  fun lpop(key: String): Supplier<ByteString?>

  fun rpop(key: String, count: Int): Supplier<List<ByteString?>>

  fun rpop(key: String): Supplier<ByteString?>

  fun lrange(key: String, start: Long, stop: Long): Supplier<List<ByteString?>>

  fun lrem(key: String, count: Long, element: ByteString): Supplier<Long>

  fun rpoplpush(sourceKey: String, destinationKey: String): Supplier<ByteString?>

  fun expire(key: String, seconds: Long): Supplier<Boolean>

  fun expireAt(key: String, timestampSeconds: Long): Supplier<Boolean>

  fun pExpire(key: String, milliseconds: Long): Supplier<Boolean>

  fun pExpireAt(key: String, timestampMilliseconds: Long): Supplier<Boolean>

  fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: Redis.ZAddOptions
  ): Supplier<Long>

  fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: Redis.ZAddOptions
  ): Supplier<Long>

  fun zscore(
    key: String,
    member: String
  ): Supplier<Double?>

  fun zrange(
    key: String,
    type: Redis.ZRangeType = Redis.ZRangeType.INDEX,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean = false,
    limit: Redis.ZRangeLimit? = null,
  ): Supplier<List<ByteString?>>

  fun zrangeWithScores(
    key: String,
    type: Redis.ZRangeType = Redis.ZRangeType.INDEX,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean = false,
    limit: Redis.ZRangeLimit? = null,
  ): Supplier<List<Pair<ByteString?, Double>>>

  fun zremRangeByRank(
    key: String,
    start: Redis.ZRangeRankMarker,
    stop: Redis.ZRangeRankMarker,
  ): Supplier<Long>

  fun llen(key: String): Supplier<Long>

  fun zcard(key: String): Supplier<Long>

  fun close()
}
