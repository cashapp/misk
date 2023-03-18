package misk.redis

import com.google.inject.Module
import com.google.inject.util.Modules
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClockModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import redis.clients.jedis.args.ListDirection
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
class FakeRedisTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(
    FakeClockModule(),
    RedisTestModule(Random(1977)), // Hardcoded random seed for hrandfield* test determinism.
  )

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

  @Test fun hsetReturnsCorrectValues() {
    val key = "prehistoric_life"
    // Add one get one.
    assertThat(redis.hset(key, "Triassic", """["archosaurs"]""".encodeUtf8()))
      .isEqualTo(1L)
    // Add several get several.
    assertThat(redis.hset(key, mapOf(
      "Jurassic" to """["dinosaurs"]""".encodeUtf8(),
      "Cretaceous" to """["feathered birds"]""".encodeUtf8(),
    )))
      .isEqualTo(2L)
    // Replace all, add none.
    assertThat(redis.hset(key, mapOf(
      "Jurassic" to """["dinosaurs", "feathered dinosaurs"]""".encodeUtf8(),
      "Cretaceous" to """["feathered birds", "fish"]""".encodeUtf8(),
    )))
      .isEqualTo(0L)
    // Replace some and add some.
    assertThat(redis.hset(key, mapOf(
      "Triassic" to """["archosaurs", "corals"]""".encodeUtf8(), // replaced
      "Paleogene" to """["primates"]""".encodeUtf8(), // added
    )))
      .isEqualTo(1L)
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
    redis.hset(
      key2,
      mapOf(
        field1 to valueKey2Field1,
        field2 to valueKey2Field2,
      )
    )

    assertThat(redis.hget(key1, field1)).isEqualTo(valueKey1Field1)
    assertThat(redis.hget(key1, field2)).isEqualTo(valueKey1Field2)
    assertThat(redis.hget(key2, field1)).isEqualTo(valueKey2Field1)
    assertThat(redis.hget(key2, field2)).isEqualTo(valueKey2Field2)

    assertThat(redis.hgetAll(key1))
      .isEqualTo(mapOf(field1 to valueKey1Field1, field2 to valueKey1Field2))

    assertThat(redis.hgetAll(key2))
      .isEqualTo(mapOf(field1 to valueKey2Field1, field2 to valueKey2Field2))

    assertThat(redis.hmget(key1, field1))
      .isEqualTo(listOf(valueKey1Field1))
    assertThat(redis.hmget(key2, field1, field2))
      .isEqualTo(listOf(valueKey2Field1, valueKey2Field2))

    redis.hdel(key2, field2)
    assertThat(redis.hmget(key2, field1, field2))
      .isEqualTo(listOf(valueKey2Field1))
  }

  @Test
  fun hdelReturnsTheRightNumbers() {
    redis.hset(
      key = "movie_years",
      hash = mapOf(
        "Star Wars" to "1977".encodeUtf8(),
        "Rogue One" to "2016".encodeUtf8(),
        "Jurassic Park" to "1993".encodeUtf8(),
        "Jurassic World" to "2015".encodeUtf8()
      )
    )

    assertThat(redis.hdel("dne", "dne")).isEqualTo(0L)
    assertThat(redis.hdel("movie_years", "dne")).isEqualTo(0L)
    assertThat(redis.hdel("movie_years", "Star Wars")).isEqualTo(1L)
    assertThat(redis.hdel("movie_years", "Rogue One", "Jurassic World", "dne")).isEqualTo(2)
  }

  @Test
  fun hlen() {
    redis.hset(
      key = "movie_years",
      hash = mapOf(
        "Star Wars" to "1977".encodeUtf8(),
        "Rogue One" to "2016".encodeUtf8(),
        "Jurassic Park" to "1993".encodeUtf8(),
        "Jurassic World" to "2015".encodeUtf8()
      )
    )

    assertThat(redis.hlen("movie_years")).isEqualTo(4L)
    assertThat(redis.hlen("dne")).isEqualTo(0L)
  }

  @Test
  fun badArgumentsToBatchSet() {
    assertThatThrownBy {
      redis.mset("key".encodeUtf8())
    }.isInstanceOf(IllegalArgumentException::class.java)
  }


  @Test fun setIfNotExists() {
    val key = "key"
    val value = "value".encodeUtf8()
    val value2 = "value2".encodeUtf8()

    // Sets value because key does not exist
    assertTrue(redis.setnx(key, value))
    assertEquals(value, redis[key])

    // Does not set value because key already exists
    assertFalse(redis.setnx(key, value2))
    assertEquals(value, redis[key])
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

    // Assert that the expiry hasn't taken affect yet
    redis.hincrBy("foo", "bar", 2)
    assertEquals("3", redis.hget("foo", "bar")?.utf8())

    // Exercise
    redis.expireAt("foo", -1)
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

  @Test fun hrandField() {
    // Setup.
    val map = mapOf(
      "Luke Skywalker" to "Mark Hamill".encodeUtf8(),
      "Princess Leah" to "Carrie Fisher".encodeUtf8(),
      "Han Solo" to "Harrison Ford".encodeUtf8(),
      "R2-D2" to "Kenny Baker".encodeUtf8()
    )
    redis.hset("star wars characters", map)

    // Test hrandfield key [count] with values.
    assertThat(redis.hrandFieldWithValues("star wars characters", 1))
      .containsExactlyEntriesOf(mapOf("Luke Skywalker" to "Mark Hamill".encodeUtf8()))

    assertThat(redis.hrandFieldWithValues("star wars characters", 20))
      .containsExactlyInAnyOrderEntriesOf(map)

    // Test hrandfield key [count].
    assertThat(redis.hrandField("star wars characters", 1))
      .containsExactly("Han Solo")

    assertThat(redis.hrandField("star wars characters", 20))
      .containsExactlyInAnyOrder(*map.keys.toTypedArray())
  }

  @Test fun hrandFieldUnsupportedCount() {
    // The Redis HRANDFIELD specification dictates different behaviour when count is negative.
    // This behaviour cannot be adhered to by our implementation until Jedis fixes this bug:
    // https://github.com/redis/jedis/issues/3017
    // We must throw on a negative count in order to avoid surprising behaviour.
    val ex1 = assertThrows<IllegalArgumentException> {
      redis.hrandField("doesn't matter", -1)
    }
    val ex2 = assertThrows<IllegalArgumentException> {
      redis.hrandFieldWithValues("doesn't matter", -1)
    }
    for (ex in listOf(ex1, ex2)) {
      assertThat(ex)
        .hasMessage("This Redis client does not support negative field counts for HRANDFIELD.")
    }

    // And it doesn't make sense to allow count=0.
    val z1 = assertThrows<IllegalArgumentException> {
      redis.hrandField("doesn't matter", 0)
    }
    val z2 = assertThrows<IllegalArgumentException> {
      redis.hrandFieldWithValues("doesn't matter", 0)
    }

    for (ex in listOf(z1, z2)) {
      assertThat(ex)
        .hasMessage("You must request at least 1 field.")
    }
  }

  @Test fun lmoveOnSeparateKeys() {
    // Setup
    val sourceKey = "foo"
    val sourceElements = listOf("bar", "bat").map { it.encodeUtf8() }
    val destinationKey = "oof"
    val destinationElements = listOf("baz".encodeUtf8())

    redis.rpush(sourceKey, *sourceElements.toTypedArray())
    redis.rpush(destinationKey, *destinationElements.toTypedArray())

    // Exercise
    val result = redis.lmove(
      sourceKey = sourceKey,
      destinationKey = destinationKey,
      from = ListDirection.RIGHT,
      to = ListDirection.LEFT,
    )

    // Verify
    assertEquals("bat".encodeUtf8(), result)
    assertEquals(listOf("bar".encodeUtf8()), redis.lrange(sourceKey, 0, -1))
    assertEquals(
      listOf("bat".encodeUtf8(), "baz".encodeUtf8()),
      redis.lrange(destinationKey, 0, -1)
    )
  }

  @Test fun lmoveOnSameKey() {
    // Setup
    val sourceKey = "foo"
    val sourceElements = listOf("bar", "bat", "baz").map { it.encodeUtf8() }

    redis.rpush(sourceKey, *sourceElements.toTypedArray())
    assertEquals(sourceElements, redis.lrange(sourceKey, 0, -1))

    // Exercise
    val result = redis.lmove(
      sourceKey = sourceKey,
      destinationKey = sourceKey,
      from = ListDirection.RIGHT,
      to = ListDirection.LEFT,
    )

    // Verify
    assertEquals("baz".encodeUtf8(), result)
    assertEquals(
      listOf("baz".encodeUtf8(), "bar".encodeUtf8(), "bat".encodeUtf8()),
      redis.lrange(sourceKey, 0, -1)
    )
  }

  @Test fun lpushCreatesList() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat").map { it.encodeUtf8() }

    // Exercise
    val result = redis.lpush(key, *elements.toTypedArray())

    assertEquals(2L, result)
    assertEquals(
      elements.asReversed(),
      redis.lrange(key, 0, -1)
    )
  }

  @Test fun lpushCreatesNewList() {
    // Setup
    val key = "foo"
    val elements = listOf("bar".encodeUtf8())

    // Exercise
    val result = redis.lpush(key, *elements.toTypedArray())

    assertEquals(1L, result)
    assertEquals(elements, redis.lrange(key, 0, -1))
  }

  @Test fun lrangeOnEntireRange() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat", "bar", "baz").map { it.encodeUtf8() }
    redis.rpush(key, *elements.toTypedArray())

    // Exercise
    val result = redis.lrange(key, 0, -1)

    // Verify
    assertEquals(elements, result)
  }

  @Test fun lrangeOnKeyThatDoesNotExist() {
    // Exercise
    val result = redis.lrange("foo", 0, -1)

    // Verify
    assertEquals(emptyList(), result)
  }

  @Test fun lrangeOnStartBeyondSize() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat", "bar", "baz").map { it.encodeUtf8() }
    redis.lpush(key, *elements.toTypedArray())

    // Exercise
    val result = redis.lrange(key, 10, 5)

    // Verify
    assertEquals(emptyList(), result)
  }

  @Test fun lrangeOnInvalidStop() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat", "bar", "baz").map { it.encodeUtf8() }
    redis.rpush(key, *elements.toTypedArray())

    // Exercise
    val result = redis.lrange(key, 3, 10)

    // Verify
    assertEquals(listOf("baz".encodeUtf8()), result)
  }

  @Test fun lremOnMultipleKeys() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat", "bar", "baz").map { it.encodeUtf8() }
    redis.rpush(key, *elements.toTypedArray())

    // Exercise
    val result = redis.lrem(key, 2, "bar".encodeUtf8())

    // Verify
    assertEquals(2L, result)
    assertEquals(
      listOf("bat".encodeUtf8(), "baz".encodeUtf8()),
      redis.lrange(key, 0, -1)
    )
  }

  @Test fun lremOnExtraMultipleKeys() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat", "bar", "baz").map { it.encodeUtf8() }
    redis.rpush(key, *elements.toTypedArray())

    // Exercise
    val result = redis.lrem(key, 1, "bar".encodeUtf8())

    // Verify
    assertEquals(1L, result)
    assertEquals(
      listOf("bat".encodeUtf8(), "bar".encodeUtf8(), "baz".encodeUtf8()),
      redis.lrange(key, 0, -1)
    )
  }

  @Test fun lremReverseWithExtraMultipleKeys() {
    // Setup
    val key = "foo"
    val elements = listOf("bar", "bat", "bar", "baz").map { it.encodeUtf8() }
    redis.rpush(key, *elements.toTypedArray())

    // Exercise
    val result = redis.lrem(key, -1, "bar".encodeUtf8())

    // Verify
    assertEquals(1L, result)
    assertEquals(
      listOf("bar".encodeUtf8(), "bat".encodeUtf8(), "baz".encodeUtf8()),
      redis.lrange(key, 0, -1)
    )
  }

  @Test fun lremOnKeyThatDoesNotExist() {
    // Exercise
    val result = redis.lrem("foo", 0, "bar".encodeUtf8())

    // Verify
    assertEquals(0L, result)
  }

  @Test fun rpoplpushOnSeparateKeys() {
    // Setup
    val sourceKey = "foo"
    val sourceElements = listOf("bar", "bat").map { it.encodeUtf8() }
    val destinationKey = "oof"
    val destinationElements = listOf("baz".encodeUtf8())

    redis.rpush(sourceKey, *sourceElements.toTypedArray())
    redis.rpush(destinationKey, *destinationElements.toTypedArray())

    // Exercise
    val result = redis.rpoplpush(
      sourceKey = sourceKey,
      destinationKey = destinationKey,
    )

    // Verify
    assertEquals("bat".encodeUtf8(), result)
    assertEquals(listOf("bar".encodeUtf8()), redis.lrange(sourceKey, 0, -1))
    assertEquals(
      listOf("bat".encodeUtf8(), "baz".encodeUtf8()),
      redis.lrange(destinationKey, 0, -1)
    )
  }

  @Test fun rpoplpushOnSameKey() {
    // Setup
    val sourceKey = "foo"
    val sourceElements = listOf("bar", "bat", "baz").map { it.encodeUtf8() }

    redis.rpush(sourceKey, *sourceElements.toTypedArray())
    assertEquals(sourceElements, redis.lrange(sourceKey, 0, -1))

    // Exercise
    val result = redis.rpoplpush(
      sourceKey = sourceKey,
      destinationKey = sourceKey,
    )

    // Verify
    assertEquals("baz".encodeUtf8(), result)
    assertEquals(
      listOf("baz".encodeUtf8(), "bar".encodeUtf8(), "bat".encodeUtf8()),
      redis.lrange(sourceKey, 0, -1)
    )
  }

  @Test fun lpushAndLpop() {
    val droids = listOf("4-LOM", "BB-8", "BD-1", "C-3PO", "IG-11", "IG-88B", "K-2SO", "R2-D2")
      .map { it.encodeUtf8() }
    redis.lpush("droids", *droids.toTypedArray())

    // Non-expired keys.
    assertThat(redis.lpop("droids")).isEqualTo("R2-D2".encodeUtf8())
    assertThat(redis.lpop("droids", 3)).containsExactlyElementsOf(
      listOf("K-2SO", "IG-88B", "IG-11").map { it.encodeUtf8() })
    assertThat(redis.lpop("droids", 99)).containsExactlyElementsOf(
      listOf("C-3PO", "BD-1", "BB-8", "4-LOM").map { it.encodeUtf8() })
    assertThat(redis.lpop("droids", 1)).isEmpty()
    assertThat(redis.lpop("droids")).isNull()

    // Expired key.
    redis.lpush("droids", *droids.toTypedArray())
    redis.expire("droids", -1)
    assertThat(redis.lpop("droids", 1)).isEmpty()
  }

  @Test fun lpushAndRpop() {
    val droids = listOf("4-LOM", "BB-8", "BD-1", "C-3PO", "IG-11", "IG-88B", "K-2SO", "R2-D2")
      .map { it.encodeUtf8() }
    redis.lpush("droids", *droids.toTypedArray())

    // Non-expired keys.
    assertThat(redis.rpop("droids")).isEqualTo("4-LOM".encodeUtf8())
    assertThat(redis.rpop("droids", 3)).containsExactlyElementsOf(
      listOf("BB-8", "BD-1", "C-3PO").map { it.encodeUtf8() })
    assertThat(redis.rpop("droids", 99)).containsExactlyElementsOf(
      listOf("IG-11", "IG-88B", "K-2SO", "R2-D2").map { it.encodeUtf8() })
    assertThat(redis.rpop("droids", 1)).isEmpty()
    assertThat(redis.rpop("droids")).isNull()


    // Expired key.
    redis.lpush("droids", *droids.toTypedArray())
    redis.expire("droids", -1)
    assertThat(redis.rpop("droids", 1)).isEmpty()
  }

  @Test fun rpushAndLpop() {
    val droids = listOf("4-LOM", "BB-8", "BD-1", "C-3PO", "IG-11", "IG-88B", "K-2SO", "R2-D2")
      .map { it.encodeUtf8() }
    redis.rpush("droids", *droids.toTypedArray())

    // Non-expired keys.
    assertThat(redis.lpop("droids")).isEqualTo("4-LOM".encodeUtf8())
    assertThat(redis.lpop("droids", 3)).containsExactlyElementsOf(
      listOf("BB-8", "BD-1", "C-3PO").map { it.encodeUtf8() })
    assertThat(redis.lpop("droids", 99)).containsExactlyElementsOf(
      listOf("IG-11", "IG-88B", "K-2SO", "R2-D2").map { it.encodeUtf8() })
    assertThat(redis.lpop("droids", 1)).isEmpty()
    assertThat(redis.lpop("droids")).isNull()

    // Expired key.
    redis.rpush("droids", *droids.toTypedArray())
    redis.expire("droids", -1)
    assertThat(redis.lpop("droids", 1)).isEmpty()
  }

  @Test fun rpushAndRpop() {
    val droids = listOf("4-LOM", "BB-8", "BD-1", "C-3PO", "IG-11", "IG-88B", "K-2SO", "R2-D2")
      .map { it.encodeUtf8() }
    redis.rpush("droids", *droids.toTypedArray())

    // Non-expired keys.
    assertThat(redis.rpop("droids")).isEqualTo("R2-D2".encodeUtf8())
    assertThat(redis.rpop("droids", 3)).containsExactlyElementsOf(
      listOf("K-2SO", "IG-88B", "IG-11").map { it.encodeUtf8() })
    assertThat(redis.rpop("droids", 99)).containsExactlyElementsOf(
      listOf("C-3PO", "BD-1", "BB-8", "4-LOM").map { it.encodeUtf8() })
    assertThat(redis.rpop("droids", 1)).isEmpty()
    assertThat(redis.rpop("droids")).isNull()

    // Expired key.
    redis.rpush("droids", *droids.toTypedArray())
    redis.expire("droids", -1)
    assertThat(redis.rpop("droids", 1)).isEmpty()
  }

  @Test fun lpushAndRpushAreOrderedCorrectly() {
    // This test is pulled directly from the Redis documentation for LPUSH and RPUSH.
    val elements = listOf("a", "b", "c").map { it.encodeUtf8() }

    redis.lpush("l", *elements.toTypedArray())
    assertThat(redis.lrange("l", 0, -1))
      .containsExactly(*elements.toList().asReversed().toTypedArray())

    redis.rpush("r", *elements.toTypedArray())
    assertThat(redis.lrange("r", 0, -1)).containsExactly(*elements.toTypedArray())
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

}
