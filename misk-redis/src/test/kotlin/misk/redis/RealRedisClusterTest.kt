package misk.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedisCluster
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.exceptions.JedisClusterOperationException
import wisp.deployment.TESTING
import kotlin.test.assertEquals
import kotlin.test.assertNull

@MiskTest
class RealRedisClusterTest : AbstractRedisTest() {

  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisClusterModule(DockerRedisCluster.config, ConnectionPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
    }
  }

  @Suppress("unused")
  @MiskExternalDependency
  private val dockerRedisCluster = DockerRedisCluster

  /**
   * Using keys that belong to different slots https://redis.io/docs/reference/cluster-spec/
   * If the key contains a "{...}" pattern, only the substring between { and } is hashed
   * therefore {k}1 and {k}2 are in the same slot
   */
  private val keys =
    listOf("{k}1", "{t}1", "{k}2", "{m}1", "{m}2", "{k}3", "{t}2")

  @Inject override lateinit var redis: Redis

  @Test
  fun `batch get and set for keys not in the same slot`() {

    // mget returns null for all keys
    assertThat(redis.mget(*keys.toTypedArray())).isEqualTo(
      listOf(
        null,
        null,
        null,
        null,
        null,
        null,
        null
      )
    )

    // Set values as the key using mset
    val keyValues = keys.flatMap { listOf(it.encodeUtf8(), it.encodeUtf8()) }.toTypedArray()
    redis.mset(*keyValues)

    // mget should return the correct values in the right order
    val values = redis.mget(*keys.toTypedArray())
    assertThat(values).isEqualTo(keys.map { it.encodeUtf8() })

  }

  @Test
  fun `batch delete for keys not in the same slot`() {
    val keysToInsert = keys.toTypedArray()
    val nonExistentKey = "nonExistentKey"
    val value = "value".encodeUtf8()

    // Set all keys except nonExistentKey
    keysToInsert.forEach { redis[it] = value }
    keysToInsert.forEach { assertEquals(value, redis[it], "Key should have been set") }
    assertNull(redis[nonExistentKey], "Key should not have been set")

    // Try deleting all three keys, only 2 should actually get deleted
    assertEquals(
      keys.size,
      redis.del(*keysToInsert, nonExistentKey),
      "${keys.size} keys should have been deleted"
    )

    // Keys should be deleted
    listOf(*keysToInsert, nonExistentKey).forEach {
      assertNull(
        redis[it],
        "Key should have been deleted"
      )
    }
  }

  @Test
  fun `atomic rpoplpush throws cross-cluster error for keys not in the same slot`() {

    assertFailsOnKeysInNotSameSlot {
      redis.rpoplpush(
        sourceKey = "{pop}1",
        destinationKey = "{push}1",
      )
    }
  }

  @Test
  fun `atomic lmove throws cross-cluster error for keys not in the same slot`() {
    assertFailsOnKeysInNotSameSlot {
      redis.lmove(
        sourceKey = "{k}1",
        destinationKey = "{t}1",
        from = ListDirection.RIGHT,
        to = ListDirection.LEFT,
      )
    }
  }

  private fun assertFailsOnKeysInNotSameSlot(block: () -> Unit) {
    assertThatThrownBy { block() }
      .isInstanceOf(JedisClusterOperationException::class.java)
      .hasMessage("Keys must belong to same hashslot.")
  }

}
