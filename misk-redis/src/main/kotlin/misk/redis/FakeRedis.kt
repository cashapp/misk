package misk.redis

import okio.ByteString
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Mimics a Redis instance for testing. */
class FakeRedis : Redis {
  @Inject lateinit var clock: Clock

  /** The value type stored in our key-value store. */
  private data class Value(
    val data: ByteString,
    val expiryInstant: Instant
  )

  /** Acts as the Redis key-value store. */
  private val keyValueStore = ConcurrentHashMap<String, Value>()
  /** A nested hash map for the hget and hset operations. */
  private val hKeyValueStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Value>>()

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
    val keyMap = hKeyValueStore[key] ?: return null
    return keyMap[field]?.data
  }

  override fun hgetAll(key: String): Map<String, ByteString>? {
    return hKeyValueStore[key]?.mapValues {
      it.value.data
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
    if (hKeyValueStore[key].isNullOrEmpty()) {
      hKeyValueStore[key] = ConcurrentHashMap()
    }
    hKeyValueStore[key]!![field] = Value(
      data = value,
      expiryInstant = Instant.MAX
    )
  }
}
