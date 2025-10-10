package misk.redis

import misk.redis.Redis.ZAddOptions.CH
import misk.redis.Redis.ZAddOptions.GT
import misk.redis.Redis.ZAddOptions.LT
import misk.redis.Redis.ZAddOptions.NX
import misk.redis.Redis.ZAddOptions.XX
import misk.redis.Redis.ZRangeIndexMarker
import misk.redis.Redis.ZRangeLimit
import misk.redis.Redis.ZRangeRankMarker
import misk.redis.Redis.ZRangeScoreMarker
import misk.redis.Redis.ZRangeType.INDEX
import misk.redis.Redis.ZRangeType.SCORE
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import redis.clients.jedis.args.ListDirection
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class AbstractRedisTest {
  abstract var redis: Redis

  @BeforeEach
  fun setUp() = redis.flushAll()

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

  @Test fun deleteHashKey() {
    val key = "hashKey"
    val field = "field1"
    val value = "value".encodeUtf8()

    // Set hash field
    redis.hset(key, field, value)
    assertEquals(value, redis.hget(key, field), "Hash field should have been set")
    assertTrue(redis.exists(key), "Hash key should exist")

    // Delete hash key
    assertTrue(redis.del(key), "Hash key should have been deleted")
    assertNull(redis.hget(key, field), "Hash field should be gone after key deletion")
    assertFalse(redis.exists(key), "Hash key should not exist after deletion")

    // Try deleting the same key again
    assertFalse(redis.del(key), "Should not have deleted anything")
  }

  @Test fun deleteListKey() {
    val key = "listKey"
    val elements = listOf("element1", "element2").map { it.encodeUtf8() }

    // Push elements to list
    redis.lpush(key, *elements.toTypedArray())
    assertEquals(2L, redis.llen(key), "List should have 2 elements")
    assertTrue(redis.exists(key), "List key should exist")

    // Delete list key
    assertTrue(redis.del(key), "List key should have been deleted")
    assertEquals(0L, redis.llen(key), "List should be empty after key deletion")
    assertFalse(redis.exists(key), "List key should not exist after deletion")

    // Try deleting the same key again
    assertFalse(redis.del(key), "Should not have deleted anything")
  }

  @Test fun deleteMixedDataTypes() {
    val stringKey = "stringKey"
    val hashKey = "hashKey"
    val listKey = "listKey"
    val nonExistentKey = "nonExistentKey"

    val stringValue = "stringValue".encodeUtf8()
    val hashField = "field1"
    val hashValue = "hashValue".encodeUtf8()
    val listElements = listOf("element1", "element2").map { it.encodeUtf8() }

    // Set up different data types
    redis[stringKey] = stringValue
    redis.hset(hashKey, hashField, hashValue)
    redis.lpush(listKey, *listElements.toTypedArray())

    // Verify all keys exist
    assertTrue(redis.exists(stringKey), "String key should exist")
    assertTrue(redis.exists(hashKey), "Hash key should exist")
    assertTrue(redis.exists(listKey), "List key should exist")
    assertFalse(redis.exists(nonExistentKey), "Non-existent key should not exist")

    // Delete all keys at once (including non-existent one)
    assertEquals(3, redis.del(stringKey, hashKey, listKey, nonExistentKey),
      "3 keys should have been deleted")

    // Verify all keys are gone
    assertFalse(redis.exists(stringKey), "String key should not exist after deletion")
    assertFalse(redis.exists(hashKey), "Hash key should not exist after deletion")
    assertFalse(redis.exists(listKey), "List key should not exist after deletion")
    assertNull(redis[stringKey], "String value should be null")
    assertNull(redis.hget(hashKey, hashField), "Hash field should be null")
    assertEquals(0L, redis.llen(listKey), "List should be empty")
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

  @Test fun blpopWithSingleKeyReturnsFirstElement() {
    val key = "queue"
    redis.rpush(key, "first".encodeUtf8(), "second".encodeUtf8(), "third".encodeUtf8())

    val result = redis.blpop(arrayOf(key), 1.0)

    assertThat(result).isNotNull()
    assertThat(result!!.first).isEqualTo(key)
    assertThat(result.second).isEqualTo("first".encodeUtf8())
    assertThat(redis.lrange(key, 0, -1)).containsExactly(
      "second".encodeUtf8(), "third".encodeUtf8()
    )
  }

  @Test fun blpopWithMultipleKeysReturnsFromFirstNonEmptyList() {
    // Hash tags ensure all keys map to the same Redis Cluster slot, required for multi-key operations
    val key1 = "{same-slot-key}queue1"
    val key2 = "{same-slot-key}queue2"
    val key3 = "{same-slot-key}queue3"

    redis.rpush(key2, "value2a".encodeUtf8(), "value2b".encodeUtf8())
    redis.rpush(key3, "value3".encodeUtf8())

    val result = redis.blpop(arrayOf(key1, key2, key3), 1.0)

    assertThat(result).isNotNull()
    assertThat(result!!.first).isEqualTo(key2)
    assertThat(result.second).isEqualTo("value2a".encodeUtf8())
    // Verify only the left element was popped from key2
    assertThat(redis.lrange(key2, 0, -1)).containsExactly("value2b".encodeUtf8())
  }

  @Test fun blpopWithEmptyKeysReturnsNull() {
    val key = "empty_queue"

    val result = redis.blpop(arrayOf(key), 0.1)

    assertThat(result).isNull()
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

    assertFailsWith<IllegalArgumentException>(
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
      assertFailsWith<IllegalArgumentException>(
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
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair)
    val expectedMapForReverse =
      mapOf(ad_9Pair, yy_6_7Pair, c_5Pair, b_5Pair)
    val start = 0
    val stop = 3

    // standard happy case. get first four lowest score members.
    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by index test - stop index exceeds the size`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    val expectedMapForReverse =
      mapOf(ad_9Pair, yy_6_7Pair, c_5Pair, b_5Pair, bb_4_5Pair, bz_2_3Pair, ba_2_3Pair)
    val start = 0
    val stop = 100

    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by index test - start is greater than stop`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf<String, Double>()
    val expectedMapForReverse = mapOf<String, Double>()
    val start = 3
    val stop = 1

    // when start is greater than stop, empty set is returned.
    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by index test - negative indices - get second last to last`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf(yy_6_7Pair, ad_9Pair)
    val expectedMapForReverse = mapOf(bz_2_3Pair, ba_2_3Pair)
    val start = -2
    val stop = -1

    // negative indices. get second last to last
    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by index test - negative indices - get last 100 or as many as there are`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    val expectedMapForReverse =
      mapOf(ad_9Pair, yy_6_7Pair, c_5Pair, b_5Pair, bb_4_5Pair, bz_2_3Pair, ba_2_3Pair)
    val start = -100
    val stop = -1

    // negative indices. get last 100 or as much there is.
    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by index test - mixed indices - get from second last to second`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf<String, Double>()
    val expectedMapForReverse = mapOf<String, Double>()
    val start = -2
    val stop = 1

    // mixed indices. empty set --> going from second last to second.
    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by index test - mixed indices - get from second to second last`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse =
      mapOf(bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair)
    val expectedMapForReverse =
      mapOf(yy_6_7Pair, c_5Pair, b_5Pair, bb_4_5Pair, bz_2_3Pair)
    val start = 1
    val stop = -2

    // mixed indices. get from second to second last
    checkZRangeIndexResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - happy case`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    val expectedMapForReverse =
      mapOf(ad_9Pair, yy_6_7Pair, c_5Pair, b_5Pair, bb_4_5Pair, bz_2_3Pair, ba_2_3Pair)
    val start = -10.0
    val stop = 10.0

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - infinity cases`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    val expectedMapForReverse =
      mapOf(ad_9Pair, yy_6_7Pair, c_5Pair, b_5Pair, bb_4_5Pair, bz_2_3Pair, ba_2_3Pair)
    val start = Double.MIN_VALUE
    val stop = Double.MAX_VALUE

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - start score exceeds stop`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf<String, Double>()
    val expectedMapForReverse = mapOf<String, Double>()
    val start = 10.0
    val stop = -10.0

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - start included, stop included`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf(bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair)
    val expectedMapForReverse = mapOf(yy_6_7Pair, c_5Pair, b_5Pair, bb_4_5Pair)
    val start = 4.5
    val stop = 6.7

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - start not included, stop included`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf(b_5Pair, c_5Pair, yy_6_7Pair)
    val expectedMapForReverse = mapOf(yy_6_7Pair, c_5Pair, b_5Pair)
    val start = ZRangeScoreMarker(4.5, false)
    val stop = ZRangeScoreMarker(6.7)

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - start not included, stop not included`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf(b_5Pair, c_5Pair)
    val expectedMapForReverse = mapOf(c_5Pair, b_5Pair)
    val start = ZRangeScoreMarker(4.5, false)
    val stop = ZRangeScoreMarker(6.7, false)

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - start included, stop not included`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedMapForNonReverse = mapOf(bb_4_5Pair, b_5Pair, c_5Pair)
    val expectedMapForReverse = mapOf(c_5Pair, b_5Pair, bb_4_5Pair)
    val start = ZRangeScoreMarker(4.5)
    val stop = ZRangeScoreMarker(6.7, false)

    checkZRangeScoreResponse(expectedMapForNonReverse, expectedMapForReverse, start, stop)
  }

  @Test fun `zrange by score test - limit cases`() {
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val m2 = mapOf(bz_2_3Pair, bb_4_5Pair, b_5Pair)
    val m3 = mapOf(yy_6_7Pair, c_5Pair, b_5Pair)
    val limit = ZRangeLimit(1, 3)

    checkZRangeScoreResponse(m2, m3, -10.0, 10.0, limit)
  }

  @Test fun `zrange by score test - limit cases negative count`() {

    // The members with same score are lex arranged.
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val m2 = mapOf(b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    val m3 = mapOf(b_5Pair, bb_4_5Pair, bz_2_3Pair, ba_2_3Pair)
    val limit = ZRangeLimit(3, -2)
    val start = -10.0
    val stop = 10.0

    checkZRangeScoreResponse(m2, m3, start, stop, limit)
  }

  @Test fun `zremRangeByRank - both ranks positive`() {
    // sorted = ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedZRangeMap = mapOf(b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)

    checkZRemRangeByRankResponse(0, 2, 3, expectedZRangeMap)
  }

  @Test fun `zremRangeByRank - start positive, stop negative`() {
    // sorted = ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedZRangeMap = mapOf(yy_6_7Pair, ad_9Pair)

    checkZRemRangeByRankResponse(0, -3, 5, expectedZRangeMap)
  }

  @Test fun `zremRangeByRank - both rank negative`() {
    // sorted = ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedZRangeMap = mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair)

    checkZRemRangeByRankResponse(-3, -1, 3, expectedZRangeMap)
  }

  @Test fun `zremRangeByRank - start negative stop positive`() {
    // sorted = ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedZRangeMap = mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair)

    checkZRemRangeByRankResponse(-4, 4, 2, expectedZRangeMap)
  }

  @Test fun `zremRangeByRank - start rank after stop`() {
    // sorted = ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedZRangeMap =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)

    checkZRemRangeByRankResponse(-4, 1, 0, expectedZRangeMap)
    checkZRemRangeByRankResponse(4, 2, 0, expectedZRangeMap)
    checkZRemRangeByRankResponse(-2, -4, 0, expectedZRangeMap)
    checkZRemRangeByRankResponse(4, -4, 0, expectedZRangeMap)
  }

  @Test fun `zremRangeByRank - multiple removes`() {
    // sorted = ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )

    val expectedZRangeMap1 =
      mapOf(ba_2_3Pair, bz_2_3Pair, bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    checkZRemRangeByRankResponse(-4, 1, 0, expectedZRangeMap1)

    val expectedZRangeMap2 = mapOf(bb_4_5Pair, b_5Pair, c_5Pair, yy_6_7Pair, ad_9Pair)
    checkZRemRangeByRankResponse(0, 1, 2, expectedZRangeMap2)

    val expectedZRangeMap3 = mapOf(c_5Pair, yy_6_7Pair, ad_9Pair)
    checkZRemRangeByRankResponse(0, 1, 2, expectedZRangeMap3)

    val expectedZRangeMap4 = mapOf<String, Double>()
    checkZRemRangeByRankResponse(0, -1, 3, expectedZRangeMap4)
  }

  @Test fun `zcard`() {
    assertEquals(0, redis.zcard(key))
    redis.zadd(
      key,
      mapOf(b_5Pair, bz_2_3Pair, bb_4_5Pair, yy_6_7Pair, ad_9Pair, c_5Pair, ba_2_3Pair)
    )
    assertEquals(7, redis.zcard(key))

    redis.zadd("foo", 4.0, "d")
    redis.zadd("foo", 4.0, "e")
    assertEquals(9, redis.zcard(key))

    redis.zremRangeByRank(key, ZRangeRankMarker(0), ZRangeRankMarker(2))
    assertEquals(6, redis.zcard(key))

    redis.zremRangeByRank(key, ZRangeRankMarker(0), ZRangeRankMarker(-1))
    assertEquals(0, redis.zcard(key))
  }

  @Test fun `getDel`() {
    val key = "key"
    val value = "value".encodeUtf8()

    redis[key] = value

    assertEquals(redis[key], value)
    assertEquals(redis.getDel(key), value)

    assertNull(redis[key])
    assertNull(redis.getDel(key))
  }

  @Test
  fun `pipelining works - regular keys`() {
    val suppliers = mutableListOf<Supplier<*>>()
    redis.pipelining {
      suppliers.addAll(
        listOf(
          set("test key", "test value".encodeUtf8()),
          get("test key"),
          del("test key"),
          setnx("keynx", "valuenx".encodeUtf8()),
          setnx("keynx", "valuenx2".encodeUtf8()),
          get("keynx"),
          getDel("keynx"),
          get("keynx"),
          incr("incr"),
          incrBy("incr", 3),
          get("incr"),
        )
      )
    }
    assertThat(suppliers.map { it.get() }).containsExactly(
      Unit,
      "test value".encodeUtf8(),
      true,
      true,
      false,
      "valuenx".encodeUtf8(),
      "valuenx".encodeUtf8(),
      null,
      1L,
      4L,
      "4".encodeUtf8(),
    )
  }

  @Test
  fun `pipelining works - multikey commands`() {
    val suppliers = mutableListOf<Supplier<*>>()

    redis.pipelining {
      suppliers.addAll(
        listOf(
          mset(
            "mkey1".encodeUtf8(),
            "mval1".encodeUtf8(),
            "mkey2".encodeUtf8(),
            "mval2".encodeUtf8()
          ),
          mget("mkey1", "mkey2"),
          del("mkey1", "mkey2"),
        )
      )
    }
    assertThat(suppliers.map { it.get() }).containsExactly(
      Unit,
      listOf(
        "mval1".encodeUtf8(),
        "mval2".encodeUtf8(),
      ),
      2
    )
  }

  @Test
  fun `pipelining works - hash keys`() {
    val suppliers = mutableListOf<Supplier<*>>()

    redis.pipelining {
      suppliers.addAll(
        listOf(
          hset("hkey", "f1", "v1".encodeUtf8()),
          hget("hkey", "f1"),
          hget("hkey", "f_dne"),
          hgetAll("hkey"),
          hlen("hkey"),
          hmget("hkey", "f1", "f_dne"),
          hincrBy("hkey", "f2", 3),
          hdel("hkey", "f2"),
          hrandField("hkey", 1),
          hrandFieldWithValues("hkey", 1),
        )
      )
    }
    assertThat(suppliers.map { it.get() }).containsExactly(
      1L,
      "v1".encodeUtf8(),
      null,
      mapOf("f1" to "v1".encodeUtf8()),
      1L,
      listOf("v1".encodeUtf8(), null),
      3L,
      1L,
      listOf("f1"),
      mapOf("f1" to "v1".encodeUtf8()),
    )
  }

  @Test
  fun `pipelining works - list keys`() {
    val suppliers = mutableListOf<Supplier<*>>()

    redis.pipelining {
      suppliers.addAll(
        listOf(
          lpush("{same-slot-key}1", "lval1".encodeUtf8(), "lval2".encodeUtf8()),
          rpush("{same-slot-key}1", "lval3".encodeUtf8(), "lval4".encodeUtf8()),
          lrange("{same-slot-key}1", 0, -1),
          lrem("{same-slot-key}1", 1, "lval1".encodeUtf8()),
          lmove("{same-slot-key}1", "{same-slot-key}2", ListDirection.LEFT, ListDirection.RIGHT),
          rpoplpush("{same-slot-key}1", "{same-slot-key}2"),
          lpop("{same-slot-key}1"),
          rpop("{same-slot-key}2"),
        )
      )
    }
    assertThat(suppliers.map { it.get() }).containsExactly(
      2L,
      4L,
      listOf(
        "lval2".encodeUtf8(),
        "lval1".encodeUtf8(),
        "lval3".encodeUtf8(),
        "lval4".encodeUtf8()
      ),
      1L,
      "lval2".encodeUtf8(),
      "lval4".encodeUtf8(),
      "lval3".encodeUtf8(),
      "lval2".encodeUtf8()
    )
  }

  @Test
  fun `pipelining works - blocking operations`() {
    val suppliers = mutableListOf<Supplier<*>>()

    redis.pipelining {
      suppliers.addAll(
        listOf(
          // Blocking operations work best under scenarios where there are multiple clients writing to the same key.
          // This is really hard to test, so we just test the commands are accepted under situations where they are guaranteed
          // not to block.
          lpush("{same-slot-key}1", "lval1".encodeUtf8(), "lval2".encodeUtf8()),
          lpush("{same-slot-key}2", "lval3".encodeUtf8(), "lval4".encodeUtf8()),
          blmove("{same-slot-key}1", "{same-slot-key}2", ListDirection.LEFT, ListDirection.RIGHT, 0.0),
          brpoplpush("{same-slot-key}1", "{same-slot-key}2", 0),
        )
      )
    }

    assertThat(suppliers.map { it.get() }).containsExactly(
      2L,
      2L,
      "lval2".encodeUtf8(),
      "lval1".encodeUtf8()
    )
  }

  @Test
  fun `pipelining works - sorted sets`() {
    val suppliers = mutableListOf<Supplier<*>>()

    redis.pipelining {
      suppliers.addAll(
        listOf(
          zadd("zkey", 1.0, "a"),
          zscore("zkey", "a"),
          zrangeWithScores("zkey", start = ZRangeIndexMarker(0), stop = ZRangeIndexMarker(-1)),
          zremRangeByRank("zkey", start = ZRangeRankMarker(0), stop = ZRangeRankMarker(-1)),
          zcard("zkey")
        )
      )
    }
    assertThat(suppliers.map { it.get() }).containsExactly(
      1L,
      1.0,
      listOf("a".encodeUtf8() to 1.0),
      1L,
      0L,
    )
  }

  @Test
  fun `pipelining captures errors`() {
    val suppliers = mutableListOf<Supplier<*>>()
    redis.pipelining {
      suppliers.addAll(
        listOf(
          set("string key", "string value".encodeUtf8()),
          incr("string key"), // Not a number.
        )
      )
    }
    assertThat(suppliers.first().get()).isEqualTo(Unit)
    assertThrows<RuntimeException> { suppliers.last().get() }
  }

  private fun checkZRemRangeByRankResponse(
    start: Long,
    stop: Long,
    expectedRemoved: Long,
    expectedScoreMap: Map<String, Double>
  ) {
    assertEquals(
      expectedRemoved,
      redis.zremRangeByRank(key, ZRangeRankMarker(start), ZRangeRankMarker(stop))
    )

    assertEquals(
      expectedScoreMap.toEncodedListOfPairs(),
      redis.zrangeWithScores(key, INDEX, ZRangeIndexMarker(0), ZRangeIndexMarker(-1))
    )
  }

  private fun checkZRangeIndexResponse(
    expectedMapForNonReverse: Map<String, Double>,
    expectedMapForReverse: Map<String, Double>,
    start: Int,
    stop: Int
  ) {
    assertEquals(
      expectedMapForNonReverse.encodedKeys(),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(start),
        ZRangeIndexMarker(stop),
      )
    )
    assertEquals(
      expectedMapForNonReverse.toEncodedListOfPairs(),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(start),
        ZRangeIndexMarker(stop)
      )
    )
    assertEquals(
      expectedMapForReverse.encodedKeys(),
      redis.zrange(
        key,
        INDEX,
        ZRangeIndexMarker(start),
        ZRangeIndexMarker(stop),
        true
      )
    )
    assertEquals(
      expectedMapForReverse.toEncodedListOfPairs(),
      redis.zrangeWithScores(
        key,
        INDEX,
        ZRangeIndexMarker(start),
        ZRangeIndexMarker(stop),
        true
      )
    )
  }

  private fun checkZRangeScoreResponse(
    expectedMapForNonReverse: Map<String, Double>,
    expectedMapForReverse: Map<String, Double>,
    start: Double,
    stop: Double,
    limit: ZRangeLimit? = null
  ) {
    checkZRangeScoreResponse(
      expectedMapForNonReverse, expectedMapForReverse,
      ZRangeScoreMarker(start), ZRangeScoreMarker(stop), limit
    )
  }

  private fun checkZRangeScoreResponse(
    expectedMapForNonReverse: Map<String, Double>,
    expectedMapForReverse: Map<String, Double>,
    start: ZRangeScoreMarker,
    stop: ZRangeScoreMarker,
    limit: ZRangeLimit? = null
  ) {
    if (limit == null) {
      assertEquals(
        expectedMapForNonReverse.encodedKeys(),
        redis.zrange(
          key,
          SCORE,
          start,
          stop
        )
      )
      assertEquals(
        expectedMapForNonReverse.toEncodedListOfPairs(),
        redis.zrangeWithScores(
          key,
          SCORE,
          start,
          stop
        )
      )
      assertEquals(
        expectedMapForReverse.encodedKeys(),
        redis.zrange(
          key,
          SCORE,
          start,
          stop,
          true
        )
      )
      assertEquals(
        expectedMapForReverse.toEncodedListOfPairs(),
        redis.zrangeWithScores(
          key,
          SCORE,
          start,
          stop,
          true
        )
      )
    } else {
      assertEquals(
        expectedMapForNonReverse.encodedKeys(),
        redis.zrange(
          key,
          SCORE,
          start,
          stop,
          limit = limit
        )
      )
      assertEquals(
        expectedMapForNonReverse.toEncodedListOfPairs(),
        redis.zrangeWithScores(
          key,
          SCORE,
          start,
          stop,
          limit = limit
        )
      )
      assertEquals(
        expectedMapForReverse.encodedKeys(),
        redis.zrange(
          key,
          SCORE,
          start,
          stop,
          true,
          limit = limit
        )
      )
      assertEquals(
        expectedMapForReverse.toEncodedListOfPairs(),
        redis.zrangeWithScores(
          key,
          SCORE,
          start,
          stop,
          true,
          limit = limit
        )
      )
    }
  }

  companion object {
    // common vars used in tests for sorted set commands
    val b_5Pair = "b" to 5.0
    val bz_2_3Pair = "bz" to 2.3
    val bb_4_5Pair = "bb" to 4.5
    val yy_6_7Pair = "yy" to 6.7
    val ad_9Pair = "ad" to 9.0
    val c_5Pair = "c" to 5.0
    val ba_2_3Pair = "ba" to 2.3
    val key = "foo"
  }

  private fun Map<String, Double>.toEncodedListOfPairs(): List<Pair<ByteString, Double>> =
    this.entries.map { Pair(it.key.encodeUtf8(), it.value) }.toList()

  private fun List<String>.encoded(): List<ByteString> = this.map { it.encodeUtf8() }.toList()

  private fun Map<String, Double>.encodedKeys(): List<ByteString> =
    this.entries.map { it.key.encodeUtf8() }.toList()

}
