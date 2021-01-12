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

  // The value type stored in our key-value store.
  private data class Value(
    val data: ByteString,
    val expiryInstant: Instant
  )

  // Acts as the Redis key-value store.
  private val keyValueStore = ConcurrentHashMap<String, Value>()

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
}
