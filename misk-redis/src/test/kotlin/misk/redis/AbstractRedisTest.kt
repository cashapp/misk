package misk.redis

import misk.redis.Redis.ZAddOptions.CH
import misk.redis.Redis.ZAddOptions.GT
import misk.redis.Redis.ZAddOptions.LT
import misk.redis.Redis.ZAddOptions.NX
import misk.redis.Redis.ZAddOptions.XX
import misk.redis.Redis.ZRangeIndexMarker
import misk.redis.Redis.ZRangeLimit
import misk.redis.Redis.ZRangeScoreMarker
import misk.redis.Redis.ZRangeType.INDEX
import misk.redis.Redis.ZRangeType.SCORE
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.exceptions.JedisDataException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class AbstractRedisTest {
  abstract var redis: Redis

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
    assertThat(redis.mget(key, unknownKey, key2, key))
      .isEqualTo(listOf(value, null, value2, value))

    assertThat(redis[key]).isEqualTo(value)
    assertThat(redis[key2]).isEqualTo(value2)
    assertThat(redis[unknownKey]).isNull()
  }

  @Test fun hsetReturnsCorrectValues() {
    val key = "prehistoric_life"
    // Add one get one.
    assertThat(redis.hset(key, "Triassic", """["archosaurs"]""".encodeUtf8())).isEqualTo(1L)
    // Add several get several.
    assertThat(
      redis.hset(
        key = key,
        hash = mapOf(
          "Jurassic" to """["dinosaurs"]""".encodeUtf8(),
          "Cretaceous" to """["feathered birds"]""".encodeUtf8(),
        )
      )
    ).isEqualTo(2L)
    // Replace all, add none.
    assertThat(
      redis.hset(
        key = key,
        hash = mapOf(
          "Jurassic" to """["dinosaurs", "feathered dinosaurs"]""".encodeUtf8(),
          "Cretaceous" to """["feathered birds", "fish"]""".encodeUtf8(),
        )
      )
    ).isEqualTo(0L)
    // Replace some and add some.
    assertThat(
      redis.hset(
        key = key,
        hash = mapOf(
          "Triassic" to """["archosaurs", "corals"]""".encodeUtf8(), // replaced
          "Paleogene" to """["primates"]""".encodeUtf8(), // added
        )
      )
    ).isEqualTo(1L)
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
    assertThat(redis.hgetAll(key1)).isEmpty()
    assertThat(redis.hgetAll(key2)).isEmpty()
    assertThat(redis.hmget(key1, field1)).containsExactly(null)
    assertThat(redis.hmget(key2, field1, field2)).containsExactly(null, null)

    // use both single field set and batch field set
    redis.hset(key1, field1, valueKey1Field1)
    redis.hset(key1, field2, valueKey1Field2)
    redis.hset(
      key = key2,
      hash = mapOf(
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
      .isEqualTo(listOf(valueKey2Field1, null))
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
    assertThat(redis.hdel("movie_years", "Rogue One", "Jurassic World", "dne"))
      .isEqualTo(2)
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
    assertThatThrownBy { redis.mset("key".encodeUtf8()) }
      .isInstanceOf(IllegalArgumentException::class.java)
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
    assertThrows<RuntimeException> { redis.incrBy(key, 3) }

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
    redis.expire("foo", -1)
    redis.hincrBy("foo", "bar", 4)

    // Verify
    assertEquals("4", redis.hget("foo", "bar")?.utf8())
  }

  @Test fun hIncrByOnInvalidData() {
    // Setup
    redis.hset("foo", "bar", "baz".encodeUtf8())

    // Verify
    assertThrows<RuntimeException> {
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
      .containsAnyOf(*map.entries.toTypedArray())
      .hasSize(1)

    assertThat(redis.hrandFieldWithValues("star wars characters", 20))
      .containsExactlyInAnyOrderEntriesOf(map)

    // Test hrandfield key [count].
    assertThat(redis.hrandField("star wars characters", 1))
      .containsAnyOf(*map.keys.toTypedArray())
      .hasSize(1)

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
      assertThat(ex).hasMessage("You must request at least 1 field.")
    }
  }

  @Test fun lmoveOnSeparateKeys() {
    // Setup
    val sourceKey = "{same-slot-key}1"
    val sourceElements = listOf("bar", "bat").map { it.encodeUtf8() }
    val destinationKey = "{same-slot-key}2"
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
    val sourceKey = "{same-slot-key}3"
    val sourceElements = listOf("bar", "bat").map { it.encodeUtf8() }
    val destinationKey = "{same-slot-key}4"
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
    assertFalse { redis.expire("foo", 1) }
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

  @Test fun `zAdd no option or only CH tests`() {
    val aMember = "a"
    val bMember = "b"

    // add new
    assertEquals(1L, redis.zadd("bar1", 1.0, aMember))
    // add new and existing
    assertEquals(1L, redis.zadd("bar1", mapOf(aMember to 2.0, bMember to 1.0)))
    assertEquals(2.0, redis.zscore("bar1", "a"))
    assertEquals(1.0, redis.zscore("bar1", "b"))

    // add new
    assertEquals(1L, redis.zadd("bar2", 1.0, aMember, CH))
    // add new and existing
    assertEquals(2L, redis.zadd("bar2", mapOf(aMember to 2.0, bMember to 1.0), CH))
    assertEquals(2.0, redis.zscore("bar2", "a"))
    assertEquals(1.0, redis.zscore("bar2", "b"))

  }

  @Test fun `zAdd NX tests`() {
    val aMember = "a"
    val bMember = "b"

    // nx: never update current member.
    redis.zadd("foo", 1.0, aMember)
    redis.zadd("bar", 1.0, aMember)

    assertEquals(0L, redis.zadd("foo", 2.0, aMember, NX))
    assertEquals(1.0, redis.zscore("foo", "a"))
    assertEquals(0L, redis.zadd("bar", 2.0, aMember, NX, CH))
    assertEquals(1.0, redis.zscore("bar", "a"))

    // add new members
    assertEquals(1L, redis.zadd("foo", 3.0, bMember, NX))
    assertEquals(3.0, redis.zscore("foo", "b"))
    assertEquals(1L, redis.zadd("bar", 3.0, bMember, NX, CH))
    assertEquals(3.0, redis.zscore("bar", "b"))
  }

  @Test fun `zAdd XX tests`() {
    val aMember = "a"

    // never add new member.
    assertEquals(0L, redis.zadd("foo", 1.0, aMember, XX))
    assertThat(redis.zscore("foo", "a")).isNull()
    assertEquals(0L, redis.zadd("bar", 1.0, aMember, XX, CH))
    assertThat(redis.zscore("bar", "a")).isNull()

    redis.zadd("foo", -1.0, aMember)
    redis.zadd("bar", -1.0, aMember)

    // update existing members.
    assertEquals(0L, redis.zadd("foo", 1.0, aMember, XX))
    assertEquals(1.0, redis.zscore("foo", "a"))
    assertEquals(1L, redis.zadd("bar", 1.0, aMember, XX, CH))
    assertEquals(1.0, redis.zscore("bar", "a"))
  }

  @Test fun `zAdd LT tests`() {
    val cMember = "c"

    // LT doesn't prevent new members from being added.
    assertEquals(1L, redis.zadd("foo", 4.0, cMember, LT))
    assertEquals(4.0, redis.zscore("foo", "c"))

    assertEquals(1L, redis.zadd("bar", 4.0, cMember, LT, CH))
    assertEquals(4.0, redis.zscore("bar", "c"))

    // LT only updates existing elements if the new score is less than the current score.
    assertEquals(0L, redis.zadd("foo", 5.0, cMember, LT))
    assertEquals(4.0, redis.zscore("foo", "c"))
    assertEquals(0L, redis.zadd("foo", 1.0, cMember, LT))
    assertEquals(1.0, redis.zscore("foo", "c"))

    assertEquals(0L, redis.zadd("bar", 5.0, cMember, LT, CH))
    assertEquals(4.0, redis.zscore("bar", "c"))
    assertEquals(1L, redis.zadd("bar", 1.0, cMember, LT, CH))
    assertEquals(1.0, redis.zscore("bar", "c"))

  }

  @Test fun `zAdd GT tests`() {
    val cMember = "c"

    // GT doesn't prevent new members from being added.
    assertEquals(1L, redis.zadd("foo", 4.0, cMember, GT))
    assertEquals(4.0, redis.zscore("foo", "c"))

    assertEquals(1L, redis.zadd("bar", 4.0, cMember, GT, CH))
    assertEquals(4.0, redis.zscore("bar", "c"))

    // GT only updates existing elements if the new score is less than the current score.
    assertEquals(0L, redis.zadd("foo", 1.0, cMember, GT))
    assertEquals(4.0, redis.zscore("foo", "c"))
    assertEquals(0L, redis.zadd("foo", 5.0, cMember, GT))
    assertEquals(5.0, redis.zscore("foo", "c"))

    assertEquals(0L, redis.zadd("bar", 1.0, cMember, GT, CH))
    assertEquals(4.0, redis.zscore("bar", "c"))
    assertEquals(1L, redis.zadd("bar", 5.0, cMember, GT, CH))
    assertEquals(5.0, redis.zscore("bar", "c"))
  }

  @Test fun `zAdd valid multiple options test`() {
    val cMember = "c"
    val dMemberBytes = "d"
    // LT XX
    // LT modifies existing if score is less than current. XX prevents adding new

    // Test not adding new.
    assertEquals(0L, redis.zadd("foo", 4.0, cMember, LT, XX))
    assertNull(redis.zscore("foo", "c"))

    assertEquals(0L, redis.zadd("bar", 4.0, cMember, LT, XX, CH))
    assertNull(redis.zscore("bar", "c"))

    // add some members to test further
    redis.zadd("foo", 4.0, cMember)
    redis.zadd("bar", 4.0, cMember)

    // Test updating existing elements if the new score is less than the current score.
    assertEquals(0L, redis.zadd("foo", 5.0, cMember, LT, XX))
    assertEquals(4.0, redis.zscore("foo", "c"))
    assertEquals(0L, redis.zadd("foo", 1.0, cMember, LT, XX))
    assertEquals(1.0, redis.zscore("foo", "c"))

    assertEquals(0L, redis.zadd("bar", 5.0, cMember, LT, XX, CH))
    assertEquals(4.0, redis.zscore("bar", "c"))
    assertEquals(1L, redis.zadd("bar", 1.0, cMember, LT, XX, CH))
    assertEquals(1.0, redis.zscore("bar", "c"))


    // GT XX
    // GT modifies existing if score is more than current. XX prevents adding new

    // Test not adding new.
    assertEquals(0L, redis.zadd("foo", 4.0, dMemberBytes, GT, XX))
    assertNull(redis.zscore("foo", "d"))

    assertEquals(0L, redis.zadd("bar", 4.0, dMemberBytes, GT, XX, CH))
    assertNull(redis.zscore("bar", "d"))

    // add some members to test further
    redis.zadd("foo", 4.0, dMemberBytes)
    redis.zadd("bar", 4.0, dMemberBytes)

    // Test updating existing elements if the new score is more than the current score.
    assertEquals(0L, redis.zadd("foo", 3.0, dMemberBytes, GT, XX))
    assertEquals(4.0, redis.zscore("foo", "d"))
    assertEquals(0L, redis.zadd("foo", 5.0, dMemberBytes, GT, XX))
    assertEquals(5.0, redis.zscore("foo", "d"))

    assertEquals(0L, redis.zadd("bar", 3.0, dMemberBytes, GT, XX, CH))
    assertEquals(4.0, redis.zscore("bar", "d"))
    assertEquals(1L, redis.zadd("bar", 5.0, dMemberBytes, GT, XX, CH))
    assertEquals(5.0, redis.zscore("bar", "d"))
  }

  @Test fun `zAdd invalid multiple options test`() {
    val aMember = "a"

    assertFailsWith<JedisDataException>(
      message = "ERR XX and NX options at the same time are not compatible",
      block = { redis.zadd("foo", 2.0, aMember, NX, XX) }
    )

    setOf(
      { redis.zadd("foo", 2.0, aMember, NX, LT) },
      { redis.zadd("foo", 2.0, aMember, NX, GT) },
      { redis.zadd("foo", 2.0, aMember, GT, LT) },
      { redis.zadd("foo", 2.0, aMember, NX, LT, GT) },
      { redis.zadd("foo", 2.0, aMember, XX, LT, GT) },
    ).forEach {
      assertFailsWith<JedisDataException>(
        message = "ERR GT, LT, and/or NX options at the same time are not compatible",
        block = { it.invoke() }
      )
    }
  }

  @Test fun zScoreTest() {
    val aMember = "a"
    val bMember = "b"
    val cMember = "c"

    redis.zadd("foo", 1.0, aMember);
    redis.zadd("foo", 10.0, bMember);
    redis.zadd("foo", 0.1, cMember);
    redis.zadd("foo", 2.0, aMember);

    assertEquals(10.0, redis.zscore("foo", "b"));

    assertEquals(0.1, redis.zscore("foo", "c"));

    assertNull(redis.zscore("foo", "s"));
  }

  @Test fun `zrange by index test - happy case`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )
    // standard happy case. get first four lowest score members.
    assertEquals(
      listOf(
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "yy".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(3)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(3)
      )
    )
    assertEquals(
      listOf(
        "ad".encodeUtf8(),
        "yy".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(3),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ad".encodeUtf8(),
          10.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(3),
        true
      )
    )
  }

  @Test fun `zrange by index test - stop index exceeds the size`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )
    // stop index exceeds the size. So stop index is the last one.
    assertEquals(
      listOf(
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "yy".encodeUtf8(),
        "ad".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(100)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "ad".encodeUtf8(),
          10.0
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(100)
      )
    )
    assertEquals(
      listOf(
        "ad".encodeUtf8(),
        "yy".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8(),
        "bz".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(100),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ad".encodeUtf8(),
          10.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(100),
        true
      )
    )
  }

  @Test fun `zrange by index test - start is greater than stop`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )
    // when start is greater than stop, empty set is returned.
    assertEquals(
      listOf(),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(3),
        ZRangeIndexMarker(1)
      )
    )
    assertEquals(
      listOf(),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(3),
        ZRangeIndexMarker(1)
      )
    )
    assertEquals(
      listOf(),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(3),
        ZRangeIndexMarker(1),
        true
      )
    )
    assertEquals(
      listOf(),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(3),
        ZRangeIndexMarker(1),
        true
      )
    )
  }

  @Test fun `zrange by index test - negative indices - get second last to last`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )

    // negative indices. get second last to last
    assertEquals(
      listOf(
        "yy".encodeUtf8(),
        "ad".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(-1)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "ad".encodeUtf8(),
          10.0
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(-1)
      )
    )
    assertEquals(
      listOf(
        "bb".encodeUtf8(),
        "bz".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(-1),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(-1),
        true
      )
    )
  }

  @Test fun `zrange by index test - negative indices - get last 100 or as many as there are`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )

    // negative indices. get last 100 or as much there is.
    assertEquals(
      listOf(
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "yy".encodeUtf8(),
        "ad".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(-100),
        ZRangeIndexMarker(-1)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "ad".encodeUtf8(),
          10.0
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(-100),
        ZRangeIndexMarker(-1)
      )
    )
    assertEquals(
      listOf(
        "ad".encodeUtf8(),
        "yy".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8(),
        "bz".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(-100),
        ZRangeIndexMarker(-1),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ad".encodeUtf8(),
          10.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(-100),
        ZRangeIndexMarker(-1),
        true
      )
    )
  }

  @Test fun `zrange by index test - mixed indices - get from second last to second`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )

    // mixed indices. empty set --> going from second last to second.
    assertEquals(
      listOf(),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(1)
      )
    )
    assertEquals(
      listOf(),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(1)
      )
    )
    assertEquals(
      listOf(),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(1),
        true
      )
    )
    assertEquals(
      listOf(),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(-2),
        ZRangeIndexMarker(1),
        true
      )
    )
  }

  @Test fun `zrange by index test - mixed indices - get from second to second last`() {
    val key = "bar1"

    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0
      )
    )

    // mixed indices. get from second to second last
    assertEquals(
      listOf(
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "yy".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(1),
        ZRangeIndexMarker(-2)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(1),
        ZRangeIndexMarker(-2)
      )
    )
    assertEquals(
      listOf(
        "yy".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(1),
        ZRangeIndexMarker(-2),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(1),
        ZRangeIndexMarker(-2),
        true
      )
    )
  }

  @Test fun `zrange by index test - same score lex sorted`() {
    val key = "bar1"

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 10.0,
        "c" to 5.0,
        "ba" to 2.3
      )
    )

    assertEquals(
      listOf(
        "ba".encodeUtf8(),
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "c".encodeUtf8(),
        "yy".encodeUtf8(),
        "ad".encodeUtf8()
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(-1)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ba".encodeUtf8(),
          2.3
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "ad".encodeUtf8(),
          10.0
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(-1)
      )
    )
    assertEquals(
      listOf(
        "ad".encodeUtf8(),
        "yy".encodeUtf8(),
        "c".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8(),
        "bz".encodeUtf8(),
        "ba".encodeUtf8(),
      ),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(-1),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ad".encodeUtf8(),
          10.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "ba".encodeUtf8(),
          2.3
        )
      ),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(0),
        ZRangeIndexMarker(-1),
        true
      )
    )
  }

  @Test fun `zrange by score test - happy case`() {
    val key = "bar1"

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 9.0,
        "c" to 5.0,
        "ba" to 2.3
      )
    )

    assertEquals(
      listOf(
        "ba".encodeUtf8(),
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "c".encodeUtf8(),
        "yy".encodeUtf8(),
        "ad".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0)
      )
    )

    assertEquals(
      listOf(
        Pair(
          "ba".encodeUtf8(),
          2.3
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "ad".encodeUtf8(),
          9.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0)
      )
    )
    assertEquals(
      listOf(
        "ad".encodeUtf8(),
        "yy".encodeUtf8(),
        "c".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8(),
        "bz".encodeUtf8(),
        "ba".encodeUtf8(),
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ad".encodeUtf8(),
          9.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "ba".encodeUtf8(),
          2.3
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0),
        true
      )
    )
  }

  @Test fun `zrange by score test - infinity cases`() {
    val key = "bar1"

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 9.0,
        "c" to 5.0,
        "ba" to 2.3
      )
    )

    assertEquals(
      listOf(
        "ba".encodeUtf8(),
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "c".encodeUtf8(),
        "yy".encodeUtf8(),
        "ad".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(Double.MIN_VALUE),
        ZRangeScoreMarker(Double.MAX_VALUE)
      )
    )

    assertEquals(
      listOf(
        Pair(
          "ba".encodeUtf8(),
          2.3
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "ad".encodeUtf8(),
          9.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(Double.MIN_VALUE),
        ZRangeScoreMarker(Double.MAX_VALUE)
      )
    )
    assertEquals(
      listOf(
        "ad".encodeUtf8(),
        "yy".encodeUtf8(),
        "c".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8(),
        "bz".encodeUtf8(),
        "ba".encodeUtf8(),
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(Double.MIN_VALUE),
        ZRangeScoreMarker(Double.MAX_VALUE),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "ad".encodeUtf8(),
          9.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "ba".encodeUtf8(),
          2.3
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(Double.MIN_VALUE),
        ZRangeScoreMarker(Double.MAX_VALUE),
        true
      )
    )
  }

  @Test fun `zrange by score test - start score exceeds stop`() {
    val key = "bar1"

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 9.0,
        "c" to 5.0,
        "ba" to 2.3
      )
    )

    assertEquals(
      listOf(),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(10.0),
        ZRangeScoreMarker(-10.0)
      )
    )

    assertEquals(
      listOf(),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(10.0),
        ZRangeScoreMarker(-10.0)
      )
    )
    assertEquals(
      listOf(),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(10.0),
        ZRangeScoreMarker(-10.0),
        true
      )
    )
    assertEquals(
      listOf(),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(10.0),
        ZRangeScoreMarker(-10.0),
        true
      )
    )
  }

  @Test fun `zrange by score test - star and stop inclusion cases`() {
    val key = "bar1"

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 9.0,
        "ba" to 2.3
      )
    )

    // start included, stop included.
    assertEquals(
      listOf(
        "bb".encodeUtf8(),
        "b".encodeUtf8(),
        "yy".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(6.7)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(6.7)
      )
    )
    assertEquals(
      listOf(
        "yy".encodeUtf8(),
        "b".encodeUtf8(),
        "bb".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(6.7),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(6.7),
        true
      )
    )

    // start not included, stop included.
    assertEquals(
      listOf(
        "b".encodeUtf8(),
        "yy".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(6.7)
      )
    )
    assertEquals(
      listOf(
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "yy".encodeUtf8(),
          6.7
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(6.7)
      )
    )
    assertEquals(
      listOf(
        "yy".encodeUtf8(),
        "b".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(6.7),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(6.7),
        true
      )
    )

    // start not included, stop not included.
    assertEquals(
      listOf("b".encodeUtf8()),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(
          6.7,
          false
        )
      )
    )
    assertEquals(
      listOf(
        Pair(
          "b".encodeUtf8(),
          5.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(
          6.7,
          false
        )
      )
    )
    assertEquals(
      listOf("b".encodeUtf8()),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(
          6.7,
          false
        ),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "b".encodeUtf8(),
          5.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(
          4.5,
          false
        ),
        ZRangeScoreMarker(
          6.7,
          false
        ),
        true
      )
    )

    // start included, stop not included.
    assertEquals(
      listOf(
        "bb".encodeUtf8(),
        "b".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(
          6.7,
          false
        )
      )
    )
    assertEquals(
      listOf(
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(
          6.7,
          false
        )
      )
    )
    assertEquals(
      listOf(
        "b".encodeUtf8(),
        "bb".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(
          6.7,
          false
        ),
        true
      )
    )
    assertEquals(
      listOf(
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(4.5),
        ZRangeScoreMarker(
          6.7,
          false
        ),
        true
      )
    )
  }

  @Test fun `zrange by score test - limit cases`() {
    val key = "bar1"

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(
        "b" to 5.0,
        "bz" to 2.3,
        "bb" to 4.5,
        "yy" to 6.7,
        "ad" to 9.0,
        "c" to 5.0,
        "ba" to 2.3
      )
    )

    assertEquals(
      listOf(
        "bz".encodeUtf8(),
        "bb".encodeUtf8(),
        "b".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0),
        limit = ZRangeLimit(
          1,
          3
        )
      )
    )

    assertEquals(
      listOf(
        Pair(
          "bz".encodeUtf8(),
          2.3
        ),
        Pair(
          "bb".encodeUtf8(),
          4.5
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        )
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0),
        limit = ZRangeLimit(
          1,
          3
        )
      )
    )
    assertEquals(
      listOf(
        "yy".encodeUtf8(),
        "c".encodeUtf8(),
        "b".encodeUtf8()
      ),
      redis.zrange(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0),
        true,
        limit = ZRangeLimit(
          1,
          3
        )
      )
    )
    assertEquals(
      listOf(
        Pair(
          "yy".encodeUtf8(),
          6.7
        ),
        Pair(
          "c".encodeUtf8(),
          5.0
        ),
        Pair(
          "b".encodeUtf8(),
          5.0
        ),
      ),
      redis.zrangeWithScores(
        key,
        SCORE,
        ZRangeScoreMarker(-10.0),
        ZRangeScoreMarker(10.0),
        true,
        limit = ZRangeLimit(
          1,
          3
        )
      )
    )
  }

}
