package misk.redis

import misk.time.FakeClock
import java.time.Instant
import javax.inject.Inject

class FakeRedis : Redis {
  @Inject lateinit var clock: FakeClock

  // The value type stored in our key-value store
  private data class Value (
    val value: String,
    val expiryInstant: Instant = Instant.MAX
  )

  // Acts as the Redis key-value store
  private val keyValueStore = HashMap<String, Value>()

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

  override fun set(key: String, value: String): String {
    keyValueStore[key] = Value(value = value)
    return value
  }

  override fun setex(key: String, seconds: Int, value: String): String {
    keyValueStore[key] = Value(value, clock.instant().plusSeconds(seconds.toLong()))
    return value
  }
}
