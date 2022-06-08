package misk.redis

import okio.ByteString
import okio.ByteString.Companion.encode
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Mimics a Redis instance for testing. */
class FakeRedis : Redis {
  @Inject lateinit var clock: Clock

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

  override fun setnx(key: String, value: ByteString) {
    synchronized(lock) {
      keyValueStore.putIfAbsent(key, Value(
        data = value,
        expiryInstant = Instant.MAX
      ))
    }
  }

  override fun setnx(key: String, expiryDuration: Duration, value: ByteString) {
    synchronized(lock) {
      keyValueStore.putIfAbsent(key, Value(
        data = value,
        expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
      ))
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
