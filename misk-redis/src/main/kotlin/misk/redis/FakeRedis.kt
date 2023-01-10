package misk.redis

import okio.ByteString
import okio.ByteString.Companion.encode
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

/** Mimics a Redis instance for testing. */
class FakeRedis : Redis {
  @Inject lateinit var clock: Clock
  @Inject @ForFakeRedis lateinit var random: Random

  private val lock = Any()

  /** The value type stored in our key-value store. */
  private data class Value<T>(
    val data: T,
    var expiryInstant: Instant
  )

  /** Acts as the Redis key-value store. */
  private val keyValueStore = ConcurrentHashMap<String, Value<ByteString>>()
  /** A nested hash map for the hget and hset operations. */
  private val hKeyValueStore =
    ConcurrentHashMap<String, Value<ConcurrentHashMap<String, ByteString>>>()
  /** A hash map for the l* list operations. */
  private val lKeyValueStore = ConcurrentHashMap<String, Value<List<ByteString>>>()

  override fun del(key: String): Boolean {
    synchronized(lock) {
      if (!keyValueStore.containsKey(key)) {
        return false
      }

      return keyValueStore.remove(key) != null
    }
  }

  override fun del(vararg keys: String): Int {
    synchronized(lock) {
      // Call delete on each key and count how many were successful
      return keys.count { del(it) }
    }
  }

  override fun mget(vararg keys: String): List<ByteString?> {
    synchronized(lock) {
      return keys.map { get(it) }
    }
  }

  override fun mset(vararg keyValues: ByteString) {
    synchronized(lock) {
      require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

      (0 until keyValues.size step 2).forEach {
        set(keyValues[it].utf8(), keyValues[it + 1])
      }
    }
  }

  override fun get(key: String): ByteString? {
    synchronized(lock) {
      val value = keyValueStore[key] ?: return null

      // Check if the key has expired
      if (clock.instant() >= value.expiryInstant) {
        keyValueStore.remove(key)
        return null
      }

      return value.data
    }
  }

  override fun hdel(key: String, vararg fields: String): Long {
    synchronized(lock) {
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
  }

  override fun hget(key: String, field: String): ByteString? {
    synchronized(lock) {
      val value = hKeyValueStore[key] ?: return null

      // Check if the key has expired
      if (clock.instant() >= value.expiryInstant) {
        hKeyValueStore.remove(key)
        return null
      }

      return value.data[field]
    }
  }

  override fun hgetAll(key: String): Map<String, ByteString>? {
    synchronized(lock) {
      val value = hKeyValueStore[key] ?: return null

      // Check if the key has expired
      if (clock.instant() >= value.expiryInstant) {
        hKeyValueStore.remove(key)
        return null
      }

      return value.data.mapValues {
        it.value
      }
    }
  }

  override fun hmget(key: String, vararg fields: String): List<ByteString?> {
    return hgetAll(key)?.filter { fields.contains(it.key) }?.values?.toList() ?: emptyList()
  }

  override fun hincrBy(key: String, field: String, increment: Long): Long {
    synchronized(lock) {
      val encodedValue = hget(key, field)?.utf8() ?: "0"
      val value = encodedValue.toLong() + increment
      hset(key, field, value.toString().encode(Charsets.UTF_8))
      return value
    }
  }

  override fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>? {
    synchronized(lock) {
      return randomFields(key, count)?.toMap()
    }
  }

  override fun hrandField(key: String, count: Long): List<String> {
    synchronized(lock) {
      return randomFields(key, count)?.map { it.first } ?: emptyList()
    }
  }

  private fun randomFields(key: String, count: Long): List<Pair<String, ByteString>>? {
    checkHrandFieldCount(count)
    return hgetAll(key)?.toList()?.shuffled(random)?.take(count.toInt())
  }

  override fun set(key: String, value: ByteString) {
    synchronized(lock) {
      // Set the key to expire at the latest possible instant
      keyValueStore[key] = Value(
        data = value,
        expiryInstant = Instant.MAX
      )
    }
  }

  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    synchronized(lock) {
      keyValueStore[key] = Value(
        data = value,
        expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
      )
    }
  }

  override fun setnx(key: String, value: ByteString): Boolean {
    return synchronized(lock) {
      keyValueStore.putIfAbsent(key, Value(
        data = value,
        expiryInstant = Instant.MAX
      )) == null
    }
  }

  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean {
    return synchronized(lock) {
      keyValueStore.putIfAbsent(key, Value(
        data = value,
        expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
      )) == null
    }
  }

  override fun hset(key: String, field: String, value: ByteString) {
    if (!hKeyValueStore.containsKey(key)) {
      hKeyValueStore[key] = Value(
        data = ConcurrentHashMap(),
        expiryInstant = Instant.MAX
      )
    }
    hKeyValueStore[key]!!.data[field] = value
  }

  override fun hset(key: String, hash: Map<String, ByteString>) {
    hash.forEach {
      hset(key, it.key, it.value)
    }
  }

  override fun incr(key: String): Long {
    synchronized(lock) {
      return incrBy(key, 1)
    }
  }

  override fun incrBy(key: String, increment: Long): Long {
    synchronized(lock) {
      val encodedValue = get(key)?.utf8() ?: "0"
      val value = encodedValue.toLong() + increment
      set(key, value.toString().encode(Charsets.UTF_8))
      return value
    }
  }

  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString? {
    // Not implements as blocking to prevent deadlocks.
    return lmove(sourceKey, destinationKey, from, to)
  }

  override fun brpoplpush(sourceKey: String, destinationKey: String, timeoutSeconds: Int) =
    blmove(
      sourceKey = sourceKey,
      destinationKey = destinationKey,
      from = ListDirection.RIGHT,
      to = ListDirection.LEFT,
      timeoutSeconds = timeoutSeconds.toDouble(),
    )

  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? {
    synchronized(lock) {
      val sourceList = lKeyValueStore[sourceKey]?.data?.toMutableList() ?: return null
      val sourceValue = when (from) {
        ListDirection.LEFT -> sourceList.removeFirst()
        ListDirection.RIGHT -> sourceList.removeLast()
      }
      lKeyValueStore[sourceKey] = Value(
        data = sourceList,
        expiryInstant = Instant.MAX,
      )

      val destinationList = lKeyValueStore[destinationKey]?.data?.toMutableList() ?: mutableListOf()
      when (to) {
        ListDirection.LEFT -> destinationList.add(index = 0, element = sourceValue)
        ListDirection.RIGHT -> destinationList.add(element = sourceValue)
      }
      lKeyValueStore[destinationKey] = Value(
        data = destinationList,
        expiryInstant = Instant.MAX,
      )
      return sourceValue
    }
  }

  override fun lpush(key: String, vararg elements: ByteString): Long {
    synchronized(lock) {
      val updated = elements.toMutableList().also {
        lKeyValueStore[key]?.data?.let { old -> it.addAll(old) }
      }
      lKeyValueStore[key] = Value(
        data = updated,
        expiryInstant = Instant.MAX,
      )
      return updated.size.toLong()
    }
  }

  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> {
    synchronized(lock) {
      val list = lKeyValueStore[key]?.data ?: return emptyList()
      if (start >= list.size) return emptyList()

      // Redis allows negative values starting from the end of the list.
      val first = if (start < 0) list.size + start else start
      val last = if (stop < 0) list.size + stop else stop

      // Redis is inclusive on both sides; Kotlin only on start.
      return list.subList(max(0, first.toInt()), min(last.toInt() + 1, list.size))
    }
  }

  override fun lrem(key: String, count: Long, element: ByteString): Long {
    synchronized(lock) {
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
      lKeyValueStore[key] = Value(
        data = list,
        expiryInstant = Instant.MAX,
      )

      return deleteCount
    }
  }

  override fun rpoplpush(sourceKey: String, destinationKey: String) = lmove(
    sourceKey = sourceKey,
    destinationKey = destinationKey,
    from = ListDirection.RIGHT,
    to = ListDirection.LEFT
  )

  override fun expire(key: String, seconds: Long): Boolean {
    synchronized(lock) {
      val ttlMillis = Duration.ofSeconds(seconds).toMillis()
      return pExpireAt(key, clock.millis().plus(ttlMillis))
    }
  }

  override fun expireAt(key: String, timestampSeconds: Long): Boolean {
    synchronized(lock) {
      val epochMillis = Instant.ofEpochSecond(timestampSeconds).toEpochMilli()
      return pExpireAt(key, epochMillis)
    }
  }

  override fun pExpire(key: String, milliseconds: Long): Boolean {
    synchronized(lock) {
      return pExpireAt(key, clock.millis().plus(milliseconds))
    }
  }

  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean {
    synchronized(lock) {
      val value = keyValueStore[key]
      val hValue = hKeyValueStore[key]
      val expiresAt = Instant.ofEpochMilli(timestampMilliseconds)

      when {
        value != null -> {
          value.expiryInstant = expiresAt
        }
        hValue != null -> {
          hValue.expiryInstant = expiresAt
        }
        else -> return false
      }
      return true
    }
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
}
