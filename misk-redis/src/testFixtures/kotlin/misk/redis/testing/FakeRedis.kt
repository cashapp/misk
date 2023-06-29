package misk.redis.testing

import misk.redis.Redis
import misk.redis.checkHrandFieldCount
import okio.ByteString
import okio.ByteString.Companion.encode
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * An in-memory key-value store which closely mimics [misk.redis.RealRedis].
 *
 * This should be used if:
 *  - It is undesirable to start an actual Redis instance via [DockerRedis] in test.
 *  - You need fine-grained control over randomness in tests
 *  - You need fine-grained control over key-expiry in tests
 *
 * Caveats:
 *  - FakeRedis does not currently support [Redis.pipelined] requests or [Redis.multi] transactions
 */
class FakeRedis @Inject constructor(
  private val clock: Clock,
  @ForFakeRedis private val random: Random,
) : Redis {
  /** The value type stored in our key-value store. */
  private data class Value<T>(val data: T, var expiryInstant: Instant)

  /** Acts as the Redis key-value store. */
  private val keyValueStore = ConcurrentHashMap<String, Value<ByteString>>()

  /** A nested hash map for hash operations. */
  private val hKeyValueStore =
    ConcurrentHashMap<String, Value<ConcurrentHashMap<String, ByteString>>>()

  /** A hash map for list operations. */
  private val lKeyValueStore = ConcurrentHashMap<String, Value<List<ByteString>>>()

  @Synchronized
  override fun del(key: String): Boolean {
    if (!keyValueStore.containsKey(key)) {
      return false
    }
    return keyValueStore.remove(key) != null
  }

  @Synchronized
  override fun del(vararg keys: String): Int = keys.count { del(it) }

  @Synchronized
  override fun mget(vararg keys: String): List<ByteString?> = keys.map { get(it) }

  @Synchronized
  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

    (keyValues.indices step 2).forEach {
      set(keyValues[it].utf8(), keyValues[it + 1])
    }
  }

  @Synchronized
  override fun get(key: String): ByteString? {
    val value = keyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      keyValueStore.remove(key)
      return null
    }
    return value.data
  }

  @Synchronized
  override fun hdel(key: String, vararg fields: String): Long {
    val value = hKeyValueStore[key] ?: return 0L

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return 0L
    }

    var countDeleted = 0L
    fields.forEach {
      if (value.data.containsKey(it)) {
        value.data.remove(it)
        countDeleted++
      }
    }
    return countDeleted
  }

  @Synchronized
  override fun hget(key: String, field: String): ByteString? {
    val value = hKeyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return null
    }
    return value.data[field]
  }

  @Synchronized
  override fun hgetAll(key: String): Map<String, ByteString> {
    val value = hKeyValueStore[key] ?: return emptyMap()

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return emptyMap()
    }
    return value.data.mapValues { it.value }
  }

  @Synchronized
  override fun hlen(key: String): Long = hKeyValueStore[key]?.data?.size?.toLong() ?: 0L

  @Synchronized
  override fun hmget(key: String, vararg fields: String): List<ByteString?> {
    val hash: Map<String, ByteString> = hKeyValueStore[key]?.data ?: emptyMap()
    return buildList {
      for (field in fields) {
        add(hash[field])
      }
    }
  }

  @Synchronized
  override fun hincrBy(key: String, field: String, increment: Long): Long {
    val encodedValue = hget(key, field)?.utf8() ?: "0"
    val value = encodedValue.toLong() + increment
    hset(key, field, value.toString().encode(Charsets.UTF_8))
    return value
  }

  @Synchronized
  override fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>? =
    randomFields(key, count)?.toMap()

  @Synchronized
  override fun hrandField(key: String, count: Long): List<String> =
    randomFields(key, count)?.map { it.first } ?: emptyList()

  private fun randomFields(key: String, count: Long): List<Pair<String, ByteString>>? {
    checkHrandFieldCount(count)
    return hgetAll(key)?.toList()?.shuffled(random)?.take(count.toInt())
  }

  @Synchronized
  override fun set(key: String, value: ByteString) {
    // Set the key to expire at the latest possible instant
    keyValueStore[key] = Value(data = value, expiryInstant = Instant.MAX)
  }

  @Synchronized
  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    keyValueStore[key] = Value(
      data = value,
      expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
    )
  }

  @Synchronized
  override fun setnx(key: String, value: ByteString): Boolean {
    return setWithExpiry(key, value, Instant.MAX)
  }

  @Synchronized
  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean {
    return setWithExpiry(key, value, clock.instant().plusSeconds(expiryDuration.seconds))
  }

  private fun setWithExpiry(key: String, value: ByteString, expiryInstant: Instant): Boolean {
    return keyValueStore.putIfAbsent(key, Value(data = value, expiryInstant)) == null
  }

  @Synchronized
  override fun hset(key: String, field: String, value: ByteString): Long {
    if (!hKeyValueStore.containsKey(key)) {
      hKeyValueStore[key] = Value(data = ConcurrentHashMap(), expiryInstant = Instant.MAX)
    }
    val newFieldCount = if (hKeyValueStore[key]!!.data[field] != null) 0L else 1L
    hKeyValueStore[key]!!.data[field] = value
    return newFieldCount
  }

  @Synchronized
  override fun hset(key: String, hash: Map<String, ByteString>): Long {
    return hash.entries.sumOf { (field, value) -> hset(key, field, value) }
  }

  @Synchronized
  override fun incr(key: String): Long = incrBy(key, 1)

  @Synchronized
  override fun incrBy(key: String, increment: Long): Long {
    val encodedValue = get(key)?.utf8() ?: "0"
    val value = encodedValue.toLong() + increment
    set(key, value.toString().encode(Charsets.UTF_8))
    return value
  }

  @Synchronized
  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString? = lmove(sourceKey, destinationKey, from, to)

  @Synchronized
  override fun brpoplpush(sourceKey: String, destinationKey: String, timeoutSeconds: Int) =
    blmove(
      sourceKey = sourceKey,
      destinationKey = destinationKey,
      from = ListDirection.RIGHT,
      to = ListDirection.LEFT,
      timeoutSeconds = timeoutSeconds.toDouble(),
    )

  @Synchronized
  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? {
    val sourceList = lKeyValueStore[sourceKey]?.data?.toMutableList() ?: return null
    val sourceValue = when (from) {
      ListDirection.LEFT -> sourceList.removeFirst()
      ListDirection.RIGHT -> sourceList.removeLast()
    }
    lKeyValueStore[sourceKey] = Value(data = sourceList, expiryInstant = Instant.MAX)

    val destinationList = lKeyValueStore[destinationKey]?.data?.toMutableList() ?: mutableListOf()
    when (to) {
      ListDirection.LEFT -> destinationList.add(index = 0, element = sourceValue)
      ListDirection.RIGHT -> destinationList.add(element = sourceValue)
    }
    lKeyValueStore[destinationKey] = Value(data = destinationList, expiryInstant = Instant.MAX)
    return sourceValue
  }

  @Synchronized
  override fun lpush(key: String, vararg elements: ByteString): Long {
    val updated = lKeyValueStore[key]?.data?.toMutableList() ?: mutableListOf()
    for (element in elements) {
      updated.add(0, element)
    }
    lKeyValueStore[key] = Value(data = updated, expiryInstant = Instant.MAX)
    return updated.size.toLong()
  }

  @Synchronized
  override fun rpush(key: String, vararg elements: ByteString): Long {
    val updated = lKeyValueStore[key]?.data?.toMutableList() ?: mutableListOf()
    updated.addAll(elements)
    lKeyValueStore[key] = Value(data = updated, expiryInstant = Instant.MAX)
    return updated.size.toLong()
  }

  @Synchronized
  override fun lpop(key: String, count: Int): List<ByteString?> {
    val value = lKeyValueStore[key] ?: Value(emptyList(), clock.instant())
    if (clock.instant() >= value.expiryInstant) {
      return emptyList()
    }
    val result = with(value) {
      data.subList(0, min(data.size, count)).toList()
    }
    lKeyValueStore[key] = value.copy(data = value.data.drop(count))
    return result
  }

  @Synchronized
  override fun lpop(key: String): ByteString? = lpop(key, count = 1).firstOrNull()

  @Synchronized
  override fun rpop(key: String, count: Int): List<ByteString?> {
    val value = lKeyValueStore[key] ?: Value(emptyList(), clock.instant())
    if (clock.instant() >= value.expiryInstant) {
      return emptyList()
    }
    val result = with(value) {
      data.takeLast(min(data.size, count)).asReversed()
    }
    lKeyValueStore[key] = value.copy(data = value.data.dropLast(count))
    return result
  }

  @Synchronized
  override fun rpop(key: String): ByteString? = rpop(key, count = 1).firstOrNull()

  @Synchronized
  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> {
    val list = lKeyValueStore[key]?.data ?: return emptyList()
    if (start >= list.size) return emptyList()

    // Redis allows negative values starting from the end of the list.
    val first = if (start < 0) list.size + start else start
    val last = if (stop < 0) list.size + stop else stop

    // Redis is inclusive on both sides; Kotlin only on start.
    return list.subList(max(0, first.toInt()), min(last.toInt() + 1, list.size))
  }

  @Synchronized
  override fun lrem(key: String, count: Long, element: ByteString): Long {
    val value = lKeyValueStore[key] ?: return 0L
    if (clock.instant() >= value.expiryInstant) {
      lKeyValueStore.remove(key)
      return 0L
    }

    val list = value.data.toMutableList()
    var totalCount = count
    val iterList = if (count < 0) {
      totalCount = -totalCount
      list.asReversed()
    } else {
      list
    }

    var deleteCount = 0L
    while ((count == 0L || deleteCount < totalCount) && iterList.contains(element)) {
      iterList.remove(element)
      deleteCount += 1
    }
    lKeyValueStore[key] = Value(data = list, expiryInstant = Instant.MAX)

    return deleteCount
  }

  @Synchronized
  override fun rpoplpush(sourceKey: String, destinationKey: String) = lmove(
    sourceKey = sourceKey,
    destinationKey = destinationKey,
    from = ListDirection.RIGHT,
    to = ListDirection.LEFT
  )

  @Synchronized
  override fun expire(key: String, seconds: Long): Boolean {
    val ttlMillis = Duration.ofSeconds(seconds).toMillis()
    return pExpireAt(key, clock.millis().plus(ttlMillis))
  }

  @Synchronized
  override fun expireAt(key: String, timestampSeconds: Long): Boolean {
    val epochMillis = Instant.ofEpochSecond(timestampSeconds).toEpochMilli()
    return pExpireAt(key, epochMillis)
  }

  @Synchronized
  override fun pExpire(key: String, milliseconds: Long): Boolean =
    pExpireAt(key, clock.millis().plus(milliseconds))

  @Synchronized
  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean {
    val value = keyValueStore[key]
    val hValue = hKeyValueStore[key]
    val lValue = lKeyValueStore[key]
    val expiresAt = Instant.ofEpochMilli(timestampMilliseconds)

    when {
      value != null -> value.expiryInstant = expiresAt
      hValue != null -> hValue.expiryInstant = expiresAt
      lValue != null -> lValue.expiryInstant = expiresAt
      else -> return false
    }
    return true
  }

  override fun watch(vararg keys: String) {
    // no op
  }

  override fun unwatch(vararg keys: String) {
    // no op
  }

  override fun multi(): Transaction {
    throw NotImplementedError("Fake client not implemented for this operation")
  }

  override fun pipelined(): Pipeline {
    throw NotImplementedError("Fake client not implemented for this operation")
  }

  override fun close() {
    // no op
  }

  override fun subscribe(jedisPubSub: JedisPubSub, channel: String) {
    throw NotImplementedError("Fake client not implemented for this operation")
  }

  override fun publish(channel: String, message: String) {
    throw NotImplementedError("Fake client not implemented for this operation")
  }
}
