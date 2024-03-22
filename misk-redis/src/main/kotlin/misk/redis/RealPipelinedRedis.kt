package misk.redis

import okio.ByteString
import okio.ByteString.Companion.toByteString
import redis.clients.jedis.AbstractPipeline
import redis.clients.jedis.ClusterPipeline
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Response
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.params.SetParams
import redis.clients.jedis.params.ZRangeParams
import redis.clients.jedis.resps.Tuple
import redis.clients.jedis.util.JedisClusterCRC16
import java.time.Duration
import java.util.function.Supplier

internal class RealPipelinedRedis(private val pipeline: AbstractPipeline) : DeferredRedis {
  private fun checkSlot(op: String, keys: List<ByteArray>): Throwable? {
    if (pipeline !is ClusterPipeline) {
      return null
    }
    val slots = keys.map { JedisClusterCRC16.getSlot(it) }.distinct()
    return if (slots.size == 1) {
      null
    } else {
      RuntimeException(
        """
          |When using clustered Redis, keys used by one $op command must always map to the same slot, but mapped to slots $slots.
          |You can use {hashtags} in your key name to control how Redis hashes keys to slots.
          |For example, keys: `{customer9001}.contacts` and `{customer9001}.payments` will  hash to the same slot.
          |
          |See https://redis.io/topics/cluster-spec#hash-tags for more information.
          |
          """.trimMargin()
      )
    }
  }

  override fun del(key: String): Supplier<Boolean> {
    val keyBytes = key.toByteArray(charset)
    val response = pipeline.del(keyBytes)
    return Supplier { response.get() == 1L }
  }

  override fun del(vararg keys: String): Supplier<Int> {
    val keysBytes = keys.map { it.toByteArray(charset) }.toTypedArray()
    val responses = when (pipeline) {
      is Pipeline -> listOf(pipeline.del(*keysBytes))
      is ClusterPipeline -> {
        keysBytes.groupBy { JedisClusterCRC16.getSlot(it) }
          .map { (_, slottedKeys) ->
            pipeline.del(*slottedKeys.toTypedArray())
          }
      }

      else -> error("Unknown pipeline type: $pipeline")
    }
    return Supplier {
      responses.fold(0) { acc, response ->
        acc + response.get().toInt()
      }
    }
  }

  override fun mget(vararg keys: String): Supplier<List<ByteString?>> {
    val keysBytes = keys.map { it.toByteArray(charset) }.toTypedArray()

    return when (pipeline) {
      is Pipeline -> {
        val response = pipeline.mget(*keysBytes)
        Supplier { response.get().map { it?.toByteString() } }
      }

      is ClusterPipeline -> {
        val responses = keysBytes.groupBy { JedisClusterCRC16.getSlot(it) }
          .mapValues { (_, slottedKeys) ->
            pipeline.mget(*slottedKeys.toTypedArray())
          }
        Supplier {
          // Stitch together the responses in the order of the original keys, as we may have run
          // multiple mgets out of order.
          val keyToValueMap = mutableMapOf<String, ByteString?>()
          keys.groupBy { JedisClusterCRC16.getSlot(it.toByteArray(charset)) }
            .flatMap { (slot, slotKeys) ->
              val result = responses[slot]?.get() ?: listOf(null)
              slotKeys.zip(result)
            }.forEach { (key, value) ->
              keyToValueMap[key] = value?.toByteString()
            }
          keys.map { keyToValueMap[it] }
        }
      }

      else -> error("Unknown pipeline type: $pipeline")
    }
  }

  override fun mset(vararg keyValues: ByteString): Supplier<Unit> {
    require(keyValues.size % 2 == 0) {
      "Wrong number of arguments to mset (must be a multiple of 2, alternating keys and values)"
    }

    val keyValuePairs = keyValues.map { it.toByteArray() }

    val responses = when (pipeline) {
      is Pipeline -> listOf(pipeline.mset(*keyValuePairs.toTypedArray()))
      is ClusterPipeline -> {
        keyValuePairs.chunked(2).groupBy { JedisClusterCRC16.getSlot(it.first()) }
          .map { (_, slottedKeyValues) ->
            pipeline.mset(*slottedKeyValues.flatten().toTypedArray())
          }
      }

      else -> error("Unknown pipeline type: $pipeline")
    }
    return Supplier { responses.map { it.get() } }
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
      response.get().associate { it.key.toString(charset) to it.value.toByteString() }
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
    checkSlot("BLMOVE", listOf(sourceKeyBytes, destKeyBytes))?.let {
      return Supplier { throw it }
    }

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
    checkSlot("BRPOPLPUSH", listOf(sourceKeyBytes, destinationKeyBytes))?.let {
      return Supplier { throw it }
    }

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
    checkSlot("LMOVE", listOf(sourceKeyBytes, destKeyBytes))?.let {
      return Supplier { throw it }
    }

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
    checkSlot("RPOPLPUSH", listOf(sourceKeyBytes, destinationKeyBytes))?.let {
      return Supplier { throw it }
    }

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

  companion object {
    /** The charset used to convert String keys to ByteArrays for Jedis commands. */
    private val charset = Charsets.UTF_8
  }
}
