package misk.redis

import misk.time.FakeClock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class FakeRedis : Redis {
  @Inject lateinit var clock: FakeClock

  // The value type stored in our key-value store
  private data class Value (
    val value: String,
    val expiryInstant: Instant
  )

  // Acts as the Redis key-value store
  private val keyValueStore = ConcurrentHashMap<String, Value>()

  override fun del(key: String): Long {
    if (!keyValueStore.containsKey(key)) return 0

    keyValueStore.remove(key)
    return 1
  }

  override fun del(vararg keys: String): Long {
    // Call delete on each key and count how many were successful
    return keys.count { del(it) == 1L }.toLong()
  }

  override fun get(key: String): String? {
    val value = keyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      keyValueStore.remove(key)
      return null
    }

    return value.value
  }

  // Sets the value for a key, the key does not expire
  override fun set(key: String, value: String): String {
    // Set the key to expire at the latest possible instant
    keyValueStore[key] = Value(value = value, expiryInstant = Instant.MAX)
    return value
  }

  override fun setex(key: String, expiryDuration: Duration, value: String): String {
    keyValueStore[key] = Value(value = value, expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds))
    return value
  }
}
