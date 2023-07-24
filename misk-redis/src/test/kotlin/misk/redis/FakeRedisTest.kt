package misk.redis

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.redis.testing.RedisTestModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import wisp.time.FakeClock
import java.time.Duration
import javax.inject.Inject
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

  @Test fun getsetWithExpiry() {
    val key = "key"
    val value1 = "value1".encodeUtf8()
    val value2 = "value2".encodeUtf8()
    val value3 = "value3".encodeUtf8()
    val value4 = "value4".encodeUtf8()
    val expiryDuration = Duration.ofSeconds(5L)

    assertNull(redis.getset(key, expiryDuration, value1), "Key should be empty")

    clock.add(Duration.ofSeconds(3))
    assertEquals(value1, redis.getset(key, expiryDuration, value2))

    clock.add(Duration.ofSeconds(4))
    assertEquals(value2, redis.getset(key, expiryDuration, value3))

    clock.add(Duration.ofSeconds(5))
    assertNull(redis.getset(key, expiryDuration, value4), "Key should be empty after expiry")
  }

  @Test fun getsetnxWithExpiry() {
    val key = "key"
    val value1 = "value1".encodeUtf8()
    val value2 = "value2".encodeUtf8()
    val value3 = "value3".encodeUtf8()
    val expiryDuration = Duration.ofSeconds(5L)

    assertNull(redis.getsetnx(key, expiryDuration, value1), "Key should be empty")

    clock.add(Duration.ofSeconds(3))
    assertEquals(value1, redis.getsetnx(key, expiryDuration, value2))

    clock.add(Duration.ofSeconds(4))
    assertNull(redis.getsetnx(key, expiryDuration, value3), "Key should have expired")

    clock.add(Duration.ofSeconds(2))
    assertEquals(value3, redis.getsetnx(key, expiryDuration, value2))
  }
}
