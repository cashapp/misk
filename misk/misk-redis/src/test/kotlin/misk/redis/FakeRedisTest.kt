package misk.redis

import com.google.inject.util.Modules
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.time.FakeClockModule
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@MiskTest
class FakeRedisTest {
  @MiskTestModule
  val module = Modules.combine(FakeClockModule(), RedisTestModule())

  @Inject lateinit var clock: FakeClock
  @Inject lateinit var redis: Redis

  @Test fun simpleSetGet() {
    val key = "test key"
    val unknownKey = "this key doesn't exist"
    val value = "test value"
    val valueOverride = "test value override"

    // Set the value and read it
    redis.set(key, value)
    assertEquals(value, redis.get(key), "Got unexpected value")

    // Overwrite the value and read it
    redis.set(key, valueOverride)
    assertEquals(valueOverride, redis.get(key), "Expected overridden value")

    // Get a key that hasn't been set
    assertNull(redis.get(unknownKey), "Key should not exist")
  }

  @Test fun setWithExpiry() {
    val key = "key"
    val value = "value"
    val expirySec = 5L

    // Set a key that expires
    redis.setex(key, Duration.ofSeconds(expirySec), value)
    assertEquals(value, redis.get(key), "Got unexpected value")

    // Key should still be there
    clock.add(Duration.ofSeconds(4))
    assertEquals(value, redis.get(key), "Got unexpected value")

    // Key should now be expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis.get(key), "Key should be expired")

    // Key should remain expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis.get(key), "Key should be expired")
  }

  @Test fun overridingResetsExpiry() {
    val key = "key"
    val value = "value"
    val expirySec = 5L

    // Set a key that expires
    redis.setex(key, Duration.ofSeconds(expirySec), value)

    // Right before the key expires, override it with a new expiry time
    clock.add(Duration.ofSeconds(4))
    redis.setex(key, Duration.ofSeconds(expirySec), value)

    // Key should not be expired
    clock.add(Duration.ofSeconds(4))
    assertNotNull(redis.get(key), "Key should be expired")

    // Key should now be expired
    clock.add(Duration.ofSeconds(1))
    assertNull(redis.get(key), "Key did not expire")
  }

  @Test fun deleteKey() {
    val key = "key"
    val unknownKey = "this key doesn't exist"
    val value = "value"

    // Set key
    redis.set(key, value)
    assertEquals(value, redis.get(key), "Got unexpected value")

    // Delete key
    assertEquals(1, redis.del(key), "1 key should have been deleted")
    assertNull(redis.get(key), "Value was not deleted")

    // Delete a key that doesn't exist
    assertEquals(0, redis.del(unknownKey), "Should not have deleted anything")
  }

  @Test fun deleteMultipleKeys() {
    val keysToInsert = listOf("key1", "key2").toTypedArray()
    val key3 = "key3"
    val value = "value"

    // Set all keys except key3
    keysToInsert.forEach { redis.set(it, value) }
    keysToInsert.forEach { assertEquals(value, redis.get(it), "Key should have been set") }
    assertNull(redis.get(key3), "Key should not have been set")

    // Try deleting all three keys, only 2 should actually get deleted
    assertEquals(2, redis.del(*keysToInsert, key3), "2 keys should have been deleted")

    // Keys should be deleted
    listOf(*keysToInsert, key3).forEach { assertNull(redis.get(it), "Key should have been deleted") }
  }
}
