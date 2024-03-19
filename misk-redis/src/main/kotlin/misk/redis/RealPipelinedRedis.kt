package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.params.SetParams
import redis.clients.jedis.params.ZRangeParams
import redis.clients.jedis.resps.Tuple
import java.time.Duration
import java.util.function.Supplier

internal class RealPipelinedRedis(private val pipeline: JedisPipeline) : DeferredRedis {
  override fun del(key: String): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.del(keyBytes)
    return Supplier { response.get() == 1L }
  }

  override fun del(vararg keys: String): Supplier<Int> {
    val keysBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
    val response = pipeline.del(*keysBytes)
    return Supplier { response.get().toInt() }
  }

  override fun mget(vararg keys: String): Supplier<List<ByteString?>> {
    val keysBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
    val response = pipeline.mget(*keysBytes)
    return Supplier { response.get().map { it?.toByteString() } }
  }

  override fun mset(vararg keyValues: ByteString): Supplier<Unit> {
    val keyValuePairs = keyValues.map { it.toByteArray() }.toTypedArray()
    val response = pipeline.mset(*keyValuePairs)
    return Supplier { response.get() }
  }

  override fun get(key: String): Supplier<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.get(keyBytes)
    return Supplier { response.get()?.toByteString() }
  }

  override fun getDel(key: String): Supplier<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.getDel(keyBytes)
    return Supplier { response.get()?.toByteString() }
  }

  override fun hdel(key: String, vararg fields: String): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val fieldsBytes = fields.map { it.toByteArray(charset) }.toTypedArray()
    val response = pipeline.hdel(keyBytes, *fieldsBytes)
    return Supplier { response.get() }
  }

  override fun hget(key: String, field: String): Supplier<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    val fieldBytes = field.toByteArray(charset)
    val response = pipeline.hget(keyBytes, fieldBytes)
    return Supplier { response.get()?.toByteString() }
  }

  override fun hgetAll(key: String): Supplier<Map<String, ByteString>?> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.hgetAll(keyBytes)
    return Supplier {
      response.get()
        ?.mapKeys { it.key.toString(charset) }
        ?.mapValues { it.value.toByteString() }
    }
  }

  override fun hlen(key: String): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.hlen(keyBytes)
    return Supplier { response.get() }
  }

  override fun hmget(key: String, vararg fields: String): Supplier<List<ByteString?>> {
    val keyBytes = key.toByteArray(charset)
    val fieldsAsByteArrays = fields.map { it.toByteArray(charset) }.toTypedArray()
    val response = pipeline.hmget(keyBytes, *fieldsAsByteArrays)
    return Supplier { response.get()?.map { it?.toByteString() } ?: emptyList() }
  }

  override fun hincrBy(key: String, field: String, increment: Long): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val fieldBytes = field.toByteArray(charset)
    val response = pipeline.hincrBy(keyBytes, fieldBytes, increment)
    return Supplier { response.get() }
  }

  override fun hrandFieldWithValues(key: String, count: Long): Supplier<Map<String, ByteString>?> {
    checkHrandFieldCount(count)
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.hrandfieldWithValues(keyBytes, count)
    return Supplier {
      response.get()
        ?.mapKeys { it.key.toString(charset) }
        ?.mapValues { it.value.toByteString() }
    }
  }

  override fun hrandField(key: String, count: Long): Supplier<List<String>> {
    checkHrandFieldCount(count)
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.hrandfield(keyBytes, count)
    return Supplier { response.get()?.map { it.toString(charset) } ?: emptyList() }
  }

  override fun set(key: String, value: ByteString, expiryDuration: Duration?): Supplier<Unit> {
    val keyBytes = key.toByteArray(charset)
    val valueBytes = value.toByteArray()
    val params = SetParams().apply {
      if (expiryDuration != null) {
        px(expiryDuration.toMillis())
      }
    }
    val response = pipeline.set(keyBytes, valueBytes, params)
    return Supplier { response.get() }
  }

  override fun setnx(key: String, value: ByteString, expiryDuration: Duration?): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val valueBytes = value.toByteArray()
    val params = SetParams().nx().apply {
      if (expiryDuration != null) px(expiryDuration.toMillis())
    }
    val response = pipeline.set(keyBytes, valueBytes, params)
    return Supplier { response.get() == "OK" }
  }

  override fun hset(key: String, field: String, value: ByteString): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val fieldBytes = field.toByteArray(charset)
    val valueBytes = value.toByteArray()
    val response = pipeline.hset(keyBytes, fieldBytes, valueBytes)
    return Supplier { response.get() }
  }

  override fun hset(key: String, hash: Map<String, ByteString>): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val hashBytes = hash.mapKeys { it.key.toByteArray(charset) }
      .mapValues { it.value.toByteArray() }
    val response = pipeline.hset(keyBytes, hashBytes)
    return Supplier { response.get() }
  }

  override fun incr(key: String): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.incr(keyBytes)
    return Supplier { response.get() }
  }

  override fun incrBy(key: String, increment: Long): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.incrBy(keyBytes, increment)
    return Supplier { response.get() }
  }

  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): Supplier<ByteString?> {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    val response = pipeline.blmove(sourceKeyBytes, destKeyBytes, from, to, timeoutSeconds)
    return Supplier { response.get()?.toByteString() }
  }

  override fun brpoplpush(
    sourceKey: String,
    destinationKey: String,
    timeoutSeconds: Int
  ): Supplier<ByteString?> {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destinationKeyBytes = destinationKey.toByteArray(charset)
    val response = pipeline.brpoplpush(sourceKeyBytes, destinationKeyBytes, timeoutSeconds)
    return Supplier { response.get()?.toByteString() }
  }

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): Supplier<ByteString?> {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destKeyBytes = destinationKey.toByteArray(charset)
    val response = pipeline.lmove(sourceKeyBytes, destKeyBytes, from, to)
    return Supplier { response.get()?.toByteString() }
  }

  override fun lpush(key: String, vararg elements: ByteString): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val byteArrays = elements.map { it.toByteArray() }.toTypedArray()
    val response = pipeline.lpush(keyBytes, *byteArrays)
    return Supplier { response.get() }
  }

  override fun rpush(key: String, vararg elements: ByteString): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val byteArrays = elements.map { it.toByteArray() }.toTypedArray()
    val response = pipeline.rpush(keyBytes, *byteArrays)
    return Supplier { response.get() }
  }

  override fun lpop(key: String, count: Int): Supplier<List<ByteString?>> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.lpop(keyBytes, count)
    return Supplier { response.get()?.map { it.toByteString() } ?: emptyList() }
  }

  override fun lpop(key: String): Supplier<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.lpop(keyBytes)
    return Supplier { response.get()?.toByteString() }
  }

  override fun rpop(key: String, count: Int): Supplier<List<ByteString?>> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.rpop(keyBytes, count)
    return Supplier { response.get()?.map { it.toByteString() } ?: emptyList() }
  }

  override fun rpop(key: String): Supplier<ByteString?> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.rpop(keyBytes)
    return Supplier { response.get()?.toByteString() }
  }

  override fun lrange(key: String, start: Long, stop: Long): Supplier<List<ByteString?>> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.lrange(keyBytes, start, stop)
    return Supplier { response.get()?.map { it.toByteString() } ?: emptyList() }
  }

  override fun lrem(key: String, count: Long, element: ByteString): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val elementBytes = element.toByteArray()
    val response = pipeline.lrem(keyBytes, count, elementBytes)
    return Supplier { response.get() }
  }

  override fun rpoplpush(sourceKey: String, destinationKey: String): Supplier<ByteString?> {
    val sourceKeyBytes = sourceKey.toByteArray(charset)
    val destinationKeyBytes = destinationKey.toByteArray(charset)
    val response = pipeline.rpoplpush(sourceKeyBytes, destinationKeyBytes)
    return Supplier { response.get()?.toByteString() }
  }

  override fun expire(key: String, seconds: Long): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.expire(keyBytes, seconds)
    return Supplier { response.get() == 1L }
  }

  override fun expireAt(key: String, timestampSeconds: Long): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.expireAt(keyBytes, timestampSeconds)
    return Supplier { response.get() == 1L }
  }

  override fun pExpire(key: String, milliseconds: Long): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.pexpire(keyBytes, milliseconds)
    return Supplier { response.get() == 1L }
  }

  override fun pExpireAt(key: String, timestampMilliseconds: Long): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.pexpireAt(keyBytes, timestampMilliseconds)
    return Supplier { response.get() == 1L }
  }

  override fun close() {
    pipeline.close()
  }

  override fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: Redis.ZAddOptions,
  ): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val memberBytes = member.toByteArray(charset)
    val params = Redis.ZAddOptions.getZAddParams(options)
    val response = pipeline.zadd(keyBytes, score, memberBytes, params)
    return Supplier { response.get() }
  }

  override fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: Redis.ZAddOptions,
  ): Supplier<Long> {
    val keyBytes = key.toByteArray(charset)
    val scoreMembersBytes = scoreMembers.mapKeys { it.key.toByteArray(charset) }
    val params = Redis.ZAddOptions.getZAddParams(options)
    val response = pipeline.zadd(keyBytes, scoreMembersBytes, params)
    return Supplier { response.get() }
  }

  override fun zscore(key: String, member: String): Supplier<Double?> {
    val keyBytes = key.toByteArray(charset)
    val memberBytes = member.toByteArray(charset)
    val response = pipeline.zscore(keyBytes, memberBytes)
    return Supplier { response.get() }
  }

  override fun zrange(
    key: String,
    type: Redis.ZRangeType,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean,
    limit: Redis.ZRangeLimit?
  ): Supplier<List<ByteString?>> {
    return Supplier {
      zrangeBase(key, type, start, stop, reverse, false, limit)
        .get().let { response ->
          response?.noScore?.map { bytes ->
            bytes?.toByteString()
          } ?: listOf()
        }
    }
  }

  override fun zrangeWithScores(
    key: String,
    type: Redis.ZRangeType,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean,
    limit: Redis.ZRangeLimit?
  ): Supplier<List<Pair<ByteString?, Double>>> {
    return Supplier {
      zrangeBase(key, type, start, stop, reverse, true, limit)
        .get().let { response ->
          response?.withScore?.map { tuple ->
            Pair(tuple.binaryElement?.toByteString(), tuple.score)
          } ?: listOf()
        }
    }
  }

  override fun zremRangeByRank(
    key: String,
    start: Redis.ZRangeRankMarker,
    stop: Redis.ZRangeRankMarker
  ): Supplier<Long> {
    val response = pipeline.zremrangeByRank(key, start.longValue, stop.longValue)
    return Supplier { response.get() }
  }

  override fun zcard(key: String): Supplier<Long> {
    val response = pipeline.zcard(key)
    return Supplier { response.get() }
  }

  private fun zrangeBase(
    key: String,
    type: Redis.ZRangeType,
    start: Redis.ZRangeMarker,
    stop: Redis.ZRangeMarker,
    reverse: Boolean,
    withScore: Boolean,
    limit: Redis.ZRangeLimit?,
  ): Supplier<ZRangeResponse?> {
    return when (type) {
      Redis.ZRangeType.INDEX ->
        zrangeByIndex(
          key, start as Redis.ZRangeIndexMarker, stop as Redis.ZRangeIndexMarker, reverse,
          withScore
        )

      Redis.ZRangeType.SCORE ->
        zrangeByScore(
          key, start as Redis.ZRangeScoreMarker, stop as Redis.ZRangeScoreMarker, reverse,
          withScore, limit
        )
    }
  }

  private fun zrangeByIndex(
    key: String,
    start: Redis.ZRangeIndexMarker,
    stop: Redis.ZRangeIndexMarker,
    reverse: Boolean,
    withScore: Boolean
  ): Supplier<ZRangeResponse?> {
    val params = ZRangeParams(
      start.intValue,
      stop.intValue
    )
    if (reverse) params.rev()

    return Supplier {
      if (withScore) {
        ZRangeResponse.withScore(
          pipeline.zrangeWithScores(
            key.toByteArray(charset),
            params
          ).get()
        )
      } else {
        ZRangeResponse.noScore(pipeline.zrange(key.toByteArray(charset), params).get())
      }
    }
  }

  private fun zrangeByScore(
    key: String,
    start: Redis.ZRangeScoreMarker,
    stop: Redis.ZRangeScoreMarker,
    reverse: Boolean,
    withScore: Boolean,
    limit: Redis.ZRangeLimit?,
  ): Supplier<ZRangeResponse?> {
    val minString = start.toString()
    val maxString = stop.toString()

    return Supplier {
      if (limit == null && !reverse && !withScore) {
        ZRangeResponse.noScore(
          pipeline.zrangeByScore(
            key.toByteArray(charset),
            minString.toByteArray(charset),
            maxString.toByteArray(charset)
          ).get()
        )
      } else if (limit == null && !reverse) {
        ZRangeResponse.withScore(
          pipeline.zrangeByScoreWithScores(
            key.toByteArray(charset),
            minString.toByteArray(charset),
            maxString.toByteArray(charset)
          ).get()
        )
      } else if (limit == null && !withScore) {
        ZRangeResponse.noScore(
          pipeline.zrevrangeByScore(
            key.toByteArray(charset),
            maxString.toByteArray(charset),
            minString.toByteArray(charset)
          ).get()
        )
      } else if (limit == null) {
        ZRangeResponse.withScore(
          pipeline.zrevrangeByScoreWithScores(
            key.toByteArray(charset),
            maxString.toByteArray(charset),
            minString.toByteArray(charset)
          ).get()
        )
      } else if (!reverse && !withScore) {
        ZRangeResponse.noScore(
          pipeline.zrangeByScore(
            key.toByteArray(charset),
            minString.toByteArray(charset),
            maxString.toByteArray(charset),
            limit.offset,
            limit.count
          ).get()
        )
      } else if (!reverse) {
        ZRangeResponse.withScore(
          pipeline.zrangeByScoreWithScores(
            key.toByteArray(charset),
            minString.toByteArray(charset),
            maxString.toByteArray(charset),
            limit.offset,
            limit.count
          ).get()
        )
      } else if (!withScore) {
        ZRangeResponse.noScore(
          pipeline.zrevrangeByScore(
            key.toByteArray(charset),
            maxString.toByteArray(charset),
            minString.toByteArray(charset),
            limit.offset,
            limit.count
          ).get()
        )
      } else {
        ZRangeResponse.withScore(
          pipeline.zrevrangeByScoreWithScores(
            key.toByteArray(charset),
            maxString.toByteArray(charset),
            minString.toByteArray(charset),
            limit.offset,
            limit.count
          ).get()
        )
      }
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

  companion object {
    /** The charset used to convert String keys to ByteArrays for Jedis commands. */
    private val charset = Charsets.UTF_8
  }
}
