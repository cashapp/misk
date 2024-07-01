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

@MiskTest
class RealRedisTest : AbstractRedisTest() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.config, ConnectionPoolConfig(), useSsl = false))
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
