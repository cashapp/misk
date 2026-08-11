package misk.redis

import jakarta.inject.Inject
import java.time.Duration
import java.util.function.Supplier
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.redis.Redis.ZRangeRankMarker
import misk.redis.testing.RedisTestModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import redis.clients.jedis.args.ListDirection

@MiskTest
class FakeRedisTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        // Hardcoded random seed for hrandfield* test determinism.
        install(RedisTestModule(Random(1977)))
      }
    }

  @Inject lateinit var clock: FakeClock
  @Inject override lateinit var redis: Redis

  @Test
  fun `transaction queues commands and resolves responses after commit`() {
    redis["transaction-key"] = "before".encodeUtf8()
    lateinit var removed: Supplier<Boolean>

    redis.transaction {
      removed = del("transaction-key")
      set("transaction-key", "after".encodeUtf8())

      assertFails { removed.get() }
      assertEquals("before".encodeUtf8(), redis["transaction-key"])
    }

    assertTrue(removed.get())
    assertEquals("after".encodeUtf8(), redis["transaction-key"])
  }

  @Test
  fun `transaction discards queued commands when the block fails`() {
    redis["transaction-key"] = "before".encodeUtf8()

    assertFails {
      redis.transaction {
        set("transaction-key", "after".encodeUtf8())
        error("boom")
      }
    }

    assertEquals("before".encodeUtf8(), redis["transaction-key"])
  }

  @Test
  fun expireInOneSecond() {
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

  @Test
  fun expireInOneSecondTimestamp() {
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

  @Test
  fun pExpireInOneMilliSecond() {
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

  @Test
  fun pExpireInOneMilliTimestamp() {
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

  @Test
  fun `zremRangeByRank keeps the sorted set's expiry`() {
    val key = "zkey"
    redis.zadd(key, mapOf("a" to 1.0, "b" to 2.0, "c" to 3.0))
    redis.expire(key, 10)

    // Trimming rebuilt the sorted set, and the rebuilt value carried expiryInstant = Instant.MAX. The key outlived the
    // deadline its writer had set, which no real server does.
    assertEquals(1, redis.zremRangeByRank(key, ZRangeRankMarker(0), ZRangeRankMarker(0)))
    assertEquals(2, redis.zcard(key))

    // Asserting both sides of the deadline pins the expiry as unchanged rather than merely still set: a trim that
    // shortened it would fail here, and one that cleared or extended it would fail below.
    clock.add(Duration.ofSeconds(9))
    assertTrue(redis.exists(key))

    clock.add(Duration.ofSeconds(1))
    assertFalse(redis.exists(key))
    assertEquals(0, redis.zcard(key))
  }

  @Test
  fun `zremRangeByRank keeps the expiry even when it removes nothing`() {
    val key = "zkey"
    redis.zadd(key, mapOf("a" to 1.0, "b" to 2.0))
    redis.expire(key, 10)

    // An empty range used to be clamped into a one-member range, so this removed a member it should not have touched.
    assertEquals(0, redis.zremRangeByRank(key, ZRangeRankMarker(5), ZRangeRankMarker(9)))
    assertEquals(2, redis.zcard(key))

    clock.add(Duration.ofSeconds(10))
    assertFalse(redis.exists(key))
  }

  @Test
  fun setWithExpiry() {
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

  @Test
  fun setIfNotExistsWithExpiry() {
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

  @Test
  fun overridingResetsExpiry() {
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

  @Test
  fun `scan for all keys with default options`() {
    val expectedKeys = mutableSetOf<String>()
    for (i in 1..100) {
      expectedKeys.add(i.toString())
      if (i <= 25) {
        redis[i.toString()] = i.toString().encodeUtf8()
      } else if (i <= 50) {
        redis.hset(i.toString(), i.toString(), i.toString().encodeUtf8())
      } else if (i <= 75) {
        redis.lpush(i.toString(), i.toString().encodeUtf8())
      } else {
        redis.zadd(i.toString(), mapOf(i.toString() to i.toDouble()))
      }
    }

    val scanResult = redis.scan("0")

    assertEquals(expectedKeys, scanResult.keys.toSet())
    assertEquals("0", scanResult.cursor)
  }

  @Test
  fun `scan for keys matching a pattern`() {
    redis["test_tag:hello"] = "a".encodeUtf8()
    redis["different_tag:1"] = "b".encodeUtf8()
    redis["test_tag:2"] = "c".encodeUtf8()
    redis["bad_test_tag:3"] = "d".encodeUtf8()

    val scanResult = redis.scan("0", matchPattern = "test_tag:*")

    val expectedKeys = listOf("test_tag:hello", "test_tag:2")

    assertTrue(scanResult.keys.containsAll(expectedKeys) && expectedKeys.containsAll(scanResult.keys))
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

  @Test
  fun `cannot set same key multiple times with different data types`() {
    val key = "mykey"
    val value = "value".encodeUtf8()
    redis[key] = value

    assertEquals(value, redis[key])

    assertFails { redis.hset(key, "field", value) }
      .also { exception -> assertContains(exception.message!!, "WRONGTYPE") }

    assertFails { redis.lpush(key, value) }.also { exception -> assertContains(exception.message!!, "WRONGTYPE") }

    assertFails { redis.zadd(key, mapOf(value.toString() to 1.0)) }
      .also { exception -> assertContains(exception.message!!, "WRONGTYPE") }
  }

  @Test
  fun listOperationsPreserveExpiry() {
    val key = "mylist"
    val expirySec = 5L

    // Create list with initial value and set expiry
    redis.lpush(key, "initial".encodeUtf8())
    redis.expire(key, expirySec)

    // Verify expiry is active before operations
    clock.add(Duration.ofSeconds(4))
    assertEquals(1, redis.llen(key))

    // Perform various list operations - all should preserve expiry
    redis.rpush(key, "added-right".encodeUtf8())
    redis.lpush(key, "added-left".encodeUtf8())
    redis.ltrim(key, 0, 10)
    redis.lrem(key, 0, "nonexistent".encodeUtf8())

    // List should still exist
    assertTrue(redis.llen(key) > 0, "List should not be empty after operations")

    // Advance past original expiry time
    clock.add(Duration.ofSeconds(2))

    // List should have expired despite the operations
    assertEquals(0, redis.llen(key), "List should have expired based on original TTL")
    assertNull(redis.lpop(key), "List should be expired")
  }

  @Test
  fun lmovePreservesExpiryOnBothKeys() {
    val sourceKey = "source"
    val destKey = "dest"

    // Create both lists with different expiry times
    redis.lpush(sourceKey, "item1".encodeUtf8(), "item2".encodeUtf8())
    redis.expire(sourceKey, 3L)

    redis.lpush(destKey, "existing".encodeUtf8())
    redis.expire(destKey, 5L)

    // Move item from source to dest
    redis.lmove(sourceKey, destKey, ListDirection.LEFT, ListDirection.RIGHT)

    // Both lists should still have their original expiry times
    // Source expires at 3s
    clock.add(Duration.ofSeconds(3))
    assertEquals(0, redis.llen(sourceKey), "Source should expire at 3s")
    assertEquals(2, redis.llen(destKey), "Dest should not expire yet")

    // Dest expires at 5s (total)
    clock.add(Duration.ofSeconds(2))
    assertEquals(0, redis.llen(destKey), "Dest should expire at 5s")
  }
}
