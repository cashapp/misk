package misk.redis

import com.google.inject.Module
import com.google.inject.util.Modules
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.time.FakeClockModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import java.time.Duration
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@MiskTest
class FakeRedisTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(FakeClockModule(), RedisTestModule())

  @Inject lateinit var clock: FakeClock
  @Inject lateinit var redis: Redis

  @Test
  fun simpleStringSetGet() {
    val key = "test key"
    val value = "test value".encodeUtf8()
    val valueOverride = "test value override".encodeUtf8()
    val unknownKey = "this key doesn't exist"

    // Set the value and read it
    redis[key] = value
    assertEquals(value, redis[key])

    // Overwrite the value and read it
    redis[key] = valueOverride
    assertEquals(valueOverride, redis[key], "Expected overridden value")

    // Get a key that hasn't been set
    assertNull(redis[unknownKey], "Key should not exist")
  }

  @Test
  fun batchGetAndSet() {
    val key = "key"
    val key2 = "key2"
    val firstValue = "firstValue".encodeUtf8()
    val value = "value".encodeUtf8()
    val value2 = "value2".encodeUtf8()
    val unknownKey = "this key doesn't exist"

    assertThat(redis.mget(key)).isEqualTo(listOf(null))
    assertThat(redis.mget(key, key2)).isEqualTo(listOf(null, null))

    redis.mset(key.encodeUtf8(), firstValue)
    assertThat(redis.mget(key)).isEqualTo(listOf(firstValue))

    redis.mset(key.encodeUtf8(), value, key2.encodeUtf8(), value2)
    assertThat(redis.mget(key)).isEqualTo(listOf(value))
    assertThat(redis.mget(key, key2)).isEqualTo(listOf(value, value2))
    assertThat(redis.mget(key2, key)).isEqualTo(listOf(value2, value))
    assertThat(redis.mget(key, unknownKey, key2, key)).isEqualTo(listOf(value, null, value2, value))

    assertThat(redis[key]).isEqualTo(value)
    assertThat(redis[key2]).isEqualTo(value2)
    assertThat(redis[unknownKey]).isNull()
  }

  @Test
  fun badArgumentsToBatchSet() {
    assertThatThrownBy {
      redis.mset("key".encodeUtf8())
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test fun setWithExpiry() {
    val key = "key"
    val value = "value".encodeUtf8()
    val expirySec = 5L

    // Set keys that expire
    redis[key, Duration.ofSeconds(expirySec)] = value
    assertEquals(value, redis[key])

    // Key should still be there
    clock.add(Duration.ofSeconds(4))
    assertEquals(value, redis[key])

    // Key should now be expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis[key], "Key should be expired")

    // Key should remain expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis[key], "Key should be expired")
  }

  @Test fun setIfNotExists() {
    val key = "key"
    val value = "value".encodeUtf8()
    val value2 = "value2".encodeUtf8()

    // Sets value because key does not exist
    redis.setnx(key, value)
    assertEquals(value, redis[key])

    // Does not set value because key already exists
    redis.setnx(key, value2)
    assertEquals(value, redis[key])
  }

  @Test fun setIfNotExistsWithExpiry() {
    val key = "key"
    val value = "value".encodeUtf8()
    val value2 = "value2".encodeUtf8()
    val expirySec = 5L

    // Sets value because key does not exist
    redis.setnx(key, Duration.ofSeconds(expirySec), value)
    assertEquals(value, redis[key])

    // Does not set value because key already exists
    redis.setnx(key, Duration.ofSeconds(expirySec), value2)
    assertEquals(value, redis[key])

    // Key should still be there
    clock.add(Duration.ofSeconds(4))
    assertEquals(value, redis[key])

    // Key should now be expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis[key], "Key should be expired")

    // Key should remain expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis[key], "Key should be expired")
  }


  @Test fun overridingResetsExpiry() {
    val key = "key"
    val value = "value".encodeUtf8()
    val expirySec = 5L

    // Set a key that expires
    redis[key, Duration.ofSeconds(expirySec)] = value

    // Right before the key expires, override it with a new expiry time
    clock.add(Duration.ofSeconds(4))
    redis[key, Duration.ofSeconds(expirySec)] = value

    // Key should not be expired
    clock.add(Duration.ofSeconds(4))
    assertNotNull(redis[key], "Key should be expired")

    // Key should now be expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis[key], "Key did not expire")
  }

  @Test fun deleteKey() {
    val key = "key"
    val value = "value".encodeUtf8()
    val unknownKey = "this key doesn't exist"

    // Set key
    redis[key] = value
    assertEquals(value, redis[key])

    // Delete key
    assertTrue(redis.del(key), "1 key should have been deleted")
    assertNull(redis[key], "Value was not deleted")

    // Delete a key that doesn't exist
    assertFalse(redis.del(unknownKey), "Should not have deleted anything")
  }

  @Test fun deleteMultipleKeys() {
    val keysToInsert = listOf("key1", "key2").toTypedArray()
    val key3 = "key3"
    val value = "value".encodeUtf8()

    // Set all keys except key3
    keysToInsert.forEach { redis[it] = value }
    keysToInsert.forEach { assertEquals(value, redis[it], "Key should have been set") }
    assertNull(redis[key3], "Key should not have been set")

    // Try deleting all three keys, only 2 should actually get deleted
    assertEquals(2, redis.del(*keysToInsert, key3), "2 keys should have been deleted")

    // Keys should be deleted
    listOf(*keysToInsert, key3).forEach { assertNull(redis[it], "Key should have been deleted") }
  }
}
