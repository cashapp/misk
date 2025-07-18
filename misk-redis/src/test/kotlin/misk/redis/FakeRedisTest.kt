package misk.redis

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.redis.testing.RedisTestModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import misk.time.FakeClock
import java.time.Duration
import jakarta.inject.Inject
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@MiskTest
class FakeRedisTest: AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module = object: KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      // Hardcoded random seed for hrandfield* test determinism.
      install(RedisTestModule(Random(1977)))
    }
  }

  @Inject lateinit var clock: FakeClock
  @Inject override lateinit var redis: Redis

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


  @Test fun setIfNotExistsWithExpiry() {
    val key = "key"
    val value = "value".encodeUtf8()
    val value2 = "value2".encodeUtf8()
    val expirySec = 5L

    // Sets value because key does not exist
    assertTrue(redis.setnx(key, Duration.ofSeconds(expirySec), value))
    assertEquals(value, redis[key])

    // Does not set value because key already exists
    assertFalse(redis.setnx(key, Duration.ofSeconds(expirySec), value2))
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

  @Test fun `scan for all keys with default options`() {
    val expectedKeys = mutableSetOf<String>()
    for (i in 1..100) {
      expectedKeys.add(i.toString())
      redis[i.toString()] = i.toString().encodeUtf8()
    }

    val scanResult = redis.scan("0")

    assertEquals(expectedKeys, scanResult.keys.toSet())
    assertEquals("0", scanResult.cursor)
  }

  @Test fun `scan for keys matching a pattern`() {
    redis["test_tag:hello"] = "a".encodeUtf8()
    redis["different_tag:1"] = "b".encodeUtf8()
    redis["test_tag:2"] = "c".encodeUtf8()
    redis["bad_test_tag:3"] = "d".encodeUtf8()

    val scanResult = redis.scan("0", matchPattern = "test_tag:*")

    val expectedKeys = listOf("test_tag:hello", "test_tag:2")

    assertTrue(scanResult.keys.containsAll(expectedKeys) &&
      expectedKeys.containsAll(scanResult.keys))
    assertEquals(expectedKeys.size, scanResult.keys.size)
  }

  @Test
  fun `ltrim with positive indices`() {
    val key = "mylist"
    redis.lpush(key, "one".encodeUtf8(), "two".encodeUtf8(), "three".encodeUtf8())

    // Trim list to retain elements from index 1 to end
    redis.ltrim(key, 1, -1)
    assertEquals(listOf("two", "one"), redis.lrange(key, 0, -1).map { it?.utf8() })
  }

  @Test
  fun `ltrim with negative indices`() {
    val key = "myotherlist"
    redis.lpush(key, "one".encodeUtf8(), "two".encodeUtf8(), "three".encodeUtf8(), "four".encodeUtf8())

    // Trim list to retain last 2 elements
    redis.ltrim(key, -2, -1)
    assertEquals(listOf("two", "one"), redis.lrange(key, 0, -1).map { it?.utf8() })
  }

}
