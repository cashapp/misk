package misk.redis

import okio.ByteString
import okio.ByteString.Companion.encode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Mimics a Redis instance for testing. */
class FakeRedis : Redis {
  @Inject lateinit var clock: Clock

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
    if (!keyValueStore.containsKey(key)) {
      return false
    }

    return keyValueStore.remove(key) != null
  }

  override fun del(vararg keys: String): Int {
    // Call delete on each key and count how many were successful
    return keys.count { del(it) }
  }

  override fun mget(vararg keys: String): List<ByteString?> {
    return keys.map { get(it) }
  }

  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

    (0 until keyValues.size step 2).forEach {
      set(keyValues[it].utf8(), keyValues[it + 1])
    }
  }

  override fun get(key: String): ByteString? {
    val value = keyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      keyValueStore.remove(key)
      return null
    }

    return value.data
  }

  override fun hget(key: String, field: String): ByteString? {
    val value = hKeyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return null
    }


    return value.data[field]
  }

  override fun hgetAll(key: String): Map<String, ByteString>? {
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

  override fun set(key: String, value: ByteString) {
    // Set the key to expire at the latest possible instant
    keyValueStore[key] = Value(
      data = value,
      expiryInstant = Instant.MAX
    )
  }

  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    keyValueStore[key] = Value(
      data = value,
      expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
    )
  }

  override fun setnx(key: String, value: ByteString) {
    keyValueStore.putIfAbsent(key, Value(
        data = value,
        expiryInstant = Instant.MAX
    ))
  }

  override fun setnx(key: String, expiryDuration: Duration, value: ByteString) {
    keyValueStore.putIfAbsent(key, Value(
        data = value,
        expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
    ))
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

  override fun incr(key: String): Long {
    return incrBy(key, 1)
  }

  override fun incrBy(key: String, increment: Long): Long {
    val encodedValue = get(key)?.utf8() ?: "0"
    val value = encodedValue.toLong() + increment
    set(key, value.toString().encode(Charsets.UTF_8))
    return value
  }

  override fun expire(key: String, seconds: Long): Long {
    val ttlMillis = Duration.ofSeconds(seconds).toMillis()
    return pExpireAt(key, clock.millis().plus(ttlMillis))
  }

  override fun expireAt(key: String, timestampSeconds: Long): Long {
    val epochMillis = Instant.ofEpochSecond(timestampSeconds).toEpochMilli()
    return pExpireAt(key, epochMillis)
  }

  override fun pExpire(key: String, milliseconds: Long): Long {
    return pExpireAt(key, clock.millis().plus(milliseconds))
  }

  override fun pExpireAt(key: String, timestampMilliseconds: Long): Long {
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
      else -> return 0
    }
    return 1
  }
}
