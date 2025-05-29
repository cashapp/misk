package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import redis.clients.jedis.ConnectionPoolConfig
import wisp.deployment.TESTING
import java.time.Duration

@MiskTest
class RealRedisTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
    }
  }

  @Inject override lateinit var redis: Redis

  // The following tests are not part of the AbstractRedisTest because as implemented right now,
  // Redis transactions are not supported in Cluster mode. This is because the Redis interface
  // leaks Jedis implementation details, and exposes the wrong type for Transactions when
  // Jedis is configured to use a cluster.

  @Test
  fun `watch and unwatch succeeds`() {
    redis.watch("key1")
    redis.watch("key2")

    redis.set("key1", "value1".encodeUtf8())

    redis.unwatch("key2")
  }

  @Test
  fun `llen returns correct length of list`() {
    val listKey = "mylist"
    assertThat(redis.llen(listKey)).isEqualTo(0L)

    redis.rpush(listKey, "a".encodeUtf8(), "b".encodeUtf8(), "c".encodeUtf8())

    assertThat(redis.llen(listKey)).isEqualTo(3L)

    redis.lpop(listKey)
    assertThat(redis.llen(listKey)).isEqualTo(2L)
  }

  @Test
  fun `transaction is completed if watched key is not modified before the EXEC command`() {
    redis.watch("key3")
    redis.multi().use { txn ->
      redis.get("key3")
      redis.set("key4", "value4".encodeUtf8())
      txn.set("key3", "value3")
      txn.exec()
    }

    assertThat(redis.get("key3")).isEqualTo("value3".encodeUtf8())
    assertThat(redis.get("key4")).isEqualTo("value4".encodeUtf8())
  }

  @Test
  fun `transaction is aborted if at least one watched key is modified before the EXEC command`() {
    redis.watch("key5", "key6")
    redis.multi().use { txn ->
      redis["key5"] = "value5".encodeUtf8()
      txn.set("key6", "value6")
      txn.exec()
    }

    assertThat(redis["key5"]).isEqualTo("value5".encodeUtf8())
    assertThat(redis["key6"]).isNull()
  }

  @Test fun `scan for all keys with default options`() {
    val expectedKeys = mutableSetOf<String>()
    for (i in 1..100) {
      expectedKeys.add(i.toString())
      redis[i.toString()] = i.toString().encodeUtf8()
    }

    val scanKeys = scanAll()

    assertThat(scanKeys).isEqualTo(expectedKeys)
  }

  @Test fun `scan for keys matching a pattern`() {
    redis["test_tag:hello"] = "a".encodeUtf8()
    redis["different_tag:1"] = "b".encodeUtf8()
    redis["test_tag:2"] = "c".encodeUtf8()
    redis["bad_test_tag:3"] = "d".encodeUtf8()

    val scanKeys = scanAll(matchPattern = "test_tag:*")

    assertThat(scanKeys).containsExactlyInAnyOrder("test_tag:hello", "test_tag:2")
  }

  @Test fun `scan for all keys with a desired hinted page size`() {
    val expectedKeys = mutableSetOf<String>()
    for (i in 1..100) {
      expectedKeys.add(i.toString())
      redis[i.toString()] = i.toString().encodeUtf8()
    }

    val scanKeys = scanAll(count=1)

    assertThat(scanKeys).isEqualTo(expectedKeys)

  }

  @Test
  fun `ltrim trims list with positive indices`() {
    val listKey = "mylist"

    redis.rpush(listKey, "one".encodeUtf8(), "two".encodeUtf8(), "three".encodeUtf8())
    redis.ltrim(listKey, 1, 2)

    assertThat(redis.lrange(listKey, 0, -1).map { it?.utf8() }).containsExactly("two", "three")
  }

  @Test
  fun `ltrim trims list with negative indices`() {
    val listKey = "mylist"

    redis.rpush(listKey, "one".encodeUtf8(), "two".encodeUtf8(), "three".encodeUtf8(), "four".encodeUtf8())
    redis.ltrim(listKey, -3, -1)

    assertThat(redis.lrange(listKey, 0, -1).map { it?.utf8() }).containsExactly("two", "three", "four")
  }

  @Test
  fun `hkeys returns all map keys within a given key`() {
    val hashKey = "myhash"

    val map = mapOf(
      "field1" to "value1".encodeUtf8(),
      "field2" to "value2".encodeUtf8(),
      "field3" to "value3".encodeUtf8()
    )

    redis.hset(hashKey, map)

    assertThat(redis.hkeys(hashKey).map { it.utf8() })
      .containsExactly("field1", "field2", "field3")
  }

  @Test
  fun `hkeys returns empty list if given key is not set`() {
    val hashKey = "myhash"

    assertThat(redis.hkeys(hashKey)).isEmpty()
  }

  @Test
  fun `persist returns true if the timeout for a key was removed`() {
    val key = "mykey"

    redis.set(key, Duration.ofSeconds(10), "value".encodeUtf8())

    assertThat(redis.persist(key)).isTrue()
  }

  @Test
  fun `persist returns false if the key does not exist`() {
    assertThat(redis.persist("persist_false_key")).isFalse()
  }

  private fun scanAll(
    initialCursor: String = "0",
    matchPattern: String? = null,
    count: Int? = null
  ): Set<String> {
    var cursor = initialCursor
    val allKeys = mutableSetOf<String>()

    do {
      val result = redis.scan(cursor, matchPattern, count)
      allKeys.addAll(result.keys)
      cursor = result.cursor
    } while (cursor != "0")

    return allKeys
  }
}
