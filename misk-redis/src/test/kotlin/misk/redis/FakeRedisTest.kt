package misk.redis

import com.google.inject.Module
import com.google.inject.util.Modules
import com.squareup.wire.durationOfSeconds
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.time.FakeClockModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import javax.inject.Inject
import kotlin.IllegalArgumentException
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
  fun hgetAndHset() {
    val key1 = "key1"
    val key2 = "key2"
    val field1 = "field1"
    val field2 = "field2"
    val valueKey1Field1 = "valueKey1Field1".encodeUtf8()
    val valueKey1Field2 = "valueKey2Field2".encodeUtf8()
    val valueKey2Field1 = "valueKey2Field1".encodeUtf8()
    val valueKey2Field2 = "valueKey2Field2".encodeUtf8()

    assertThat(redis.hget(key1, field1)).isNull()
    assertThat(redis.hget(key1, field2)).isNull()
    assertThat(redis.hget(key2, field1)).isNull()
    assertThat(redis.hget(key2, field2)).isNull()
    assertThat(redis.hgetAll(key1)).isNull()
    assertThat(redis.hgetAll(key2)).isNull()
    assertThat(redis.hmget(key1, field1)).isEmpty()
    assertThat(redis.hmget(key2, field1)).isEmpty()

    // use both single field set and batch field set
    redis.hset(key1, field1, valueKey1Field1)
    redis.hset(key1, field2, valueKey1Field2)
    redis.hset(key2, mapOf(
      field1 to valueKey2Field1,
      field2 to valueKey2Field2,
    ))

    assertThat(redis.hget(key1, field1)).isEqualTo(valueKey1Field1)
    assertThat(redis.hget(key1, field2)).isEqualTo(valueKey1Field2)
    assertThat(redis.hget(key2, field1)).isEqualTo(valueKey2Field1)
    assertThat(redis.hget(key2, field2)).isEqualTo(valueKey2Field2)

    assertThat(redis.hgetAll(key1)).isEqualTo(mapOf(
      field1 to valueKey1Field1,
      field2 to valueKey1Field2
    ))

    assertThat(redis.hgetAll(key2)).isEqualTo(mapOf(
      field1 to valueKey2Field1,
      field2 to valueKey2Field2
    ))

    assertThat(redis.hmget(key1, field1)).isEqualTo(listOf(
      valueKey1Field1
    ))
    assertThat(redis.hmget(key2, field1, field2)).isEqualTo(listOf(
      valueKey2Field1,
      valueKey2Field2
    ))

    redis.hdel(key2, field2)
    assertThat(redis.hmget(key2, field1, field2)).isEqualTo(listOf(
      valueKey2Field1
    ))
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

  @Test fun incrOnKeyThatDoesNotExist() {
    // Setup
    val key = "does_not_exist_at_first"
    assertNull(redis[key])

    // Exercise
    val result = redis.incr(key)

    // Verify
    assertEquals(1, result)
    assertEquals("1".encodeUtf8(), redis[key])
  }

  @Test fun incrOnKeyThatExists() {
    // Setup
    val key = "bla"
    redis.incr(key)

    // Exercise
    val result = redis.incr(key)

    assertEquals(2, result)
    assertEquals("2".encodeUtf8(), redis[key])
  }

  @Test fun incrBy() {
    // Setup
    val key = "bla"

    // Exercise
    val result = redis.incrBy(key, 3)

    assertEquals(3, result)
    assertEquals("3".encodeUtf8(), redis[key])
  }

  @Test fun incrOnInvalidData() {
    // Setup
    val key = "bla"
    redis[key] = "Not a number".encodeUtf8()

    // Exercise
    assertThrows<IllegalArgumentException> {
      redis.incrBy(key, 3)
    }

    // Verify
    assertEquals("Not a number".encodeUtf8(), redis[key])
  }

  @Test fun expireOnHValueImmediately() {
    // Setup
    redis.hset("foo", "bar", "baz".encodeUtf8())

    // Exercise
    // Expire immediately
    redis.expire("foo", -1)

    // Verify
    assertNull(redis.hget("foo", "bar"))
    assertNull(redis["foo"])
  }

  @Test fun hIncrBySupportsExpiry() {
    // Setup
    redis.hset("foo", "bar", "1".encodeUtf8())
    redis.expire("foo", 1)

    // Assert that the expiry hasn't taken affect yet
    redis.hincrBy("foo", "bar", 2)
    assertEquals("3", redis.hget("foo", "bar")?.utf8())

    // Exercise
    clock.add(Duration.ofSeconds(1))
    redis.hincrBy("foo", "bar", 4)

    // Verify
    assertEquals("4", redis.hget("foo", "bar")?.utf8())
  }

  @Test fun hIncrByOnInvalidData() {
    // Setup
    redis.hset("foo", "bar", "baz".encodeUtf8())

    // Verify
    assertThrows<IllegalArgumentException> {
      redis.hincrBy("foo", "bar", 2)
    }
  }

  @Test fun hIncrByOnKeyThatDoesNotExist() {
    // Exercise
    redis.hincrBy("foo", "bar", 2)

    // Verify
    assertEquals("2", redis.hget("foo", "bar")?.utf8())
  }

  @Test fun hIncrByOnFieldThatDoesNotExist() {
    // Setup
    redis.hincrBy("foo", "baz", 3)

    // Exercise
    redis.hincrBy("foo", "bar", 2)

    // Verify
    assertEquals("3", redis.hget("foo", "baz")?.utf8())
    assertEquals("2", redis.hget("foo", "bar")?.utf8())
  }

  @Test fun expireImmediately() {
    // Setup
    redis["foo"] = "baz".encodeUtf8()

    // Exercise
    // Expire immediately
    redis.expire("foo", -1)

    // Verify
    assertNull(redis.hget("foo", "bar"))
    assertNull(redis["foo"])
  }

  @Test fun expireInOneSecond() {
    // Setup
    redis["foo"] = "baz".encodeUtf8()

    // Exercise
    // Expire in one second
    redis.expire("foo", 1)

    // Verify
    assertEquals("baz".encodeUtf8(), redis["foo"])
    clock.add(Duration.ofSeconds(1))
    assertNull(redis["foo"])
  }

  @Test fun expireInOneSecondTimestamp() {
    // Setup
    redis["foo"] = "baz".encodeUtf8()

    // Exercise
    // Expire in one second
    redis.expireAt("foo", clock.instant().plusSeconds(1).epochSecond)

    // Verify
    assertEquals("baz".encodeUtf8(), redis["foo"])
    clock.add(Duration.ofSeconds(1))
    assertNull(redis["foo"])
  }

  @Test fun pExpireInOneMilliSecond() {
    // Setup
    redis["foo"] = "baz".encodeUtf8()

    // Exercise
    // Expire in one milli
    redis.pExpire("foo", 1)

    // Verify
    assertEquals("baz".encodeUtf8(), redis["foo"])
    clock.add(Duration.ofMillis(1))
    assertNull(redis["foo"])
  }

  @Test fun pExpireInOneMilliTimestamp() {
    // Setup
    redis["foo"] = "baz".encodeUtf8()

    // Exercise
    // Expire in one milli
    redis.pExpireAt("foo", clock.instant().plusMillis(1).toEpochMilli())

    // Verify
    assertEquals("baz".encodeUtf8(), redis["foo"])
    clock.add(Duration.ofMillis(1))
    assertNull(redis["foo"])
  }

  @Test fun expireOnKeyThatExists() {
    // Setup
    redis["foo"] = "bar".encodeUtf8()

    // Exercise
    assertTrue {
      redis.expire("foo", 1)
    }
  }

  @Test fun expireOnKeyThatDoesNotExist() {
    // Exercise
    assertFalse {
      redis.expire("foo", 1)
    }
  }
}
