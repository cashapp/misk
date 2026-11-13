package misk.redis.lettuce.cluster

import com.google.inject.Provider
import io.lettuce.core.FlushMode
import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import misk.redis.lettuce.redisSeedPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class ClusterFunctionCodeLoaderTest {
  private val client = RedisClusterClient.create(RedisURI.create("localhost", redisSeedPort))

  @AfterEach
  fun tearDown() {
    client.shutdown()
  }

  @Test
  fun `loads function code on every primary`() {
    client.connect(StringCodec.UTF8).use { connection ->
      val primaries = connection.sync().upstream()
      primaries.commands().functionFlush(FlushMode.SYNC)

      ClusterFunctionCodeLoader(Provider { client }, "redis/testlib.lua")
        .load()
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS)

      assertEquals(
        List(primaries.size()) { listOf("testlib") },
        primaries.commands().functionList().map { libraries -> libraries.map { it["library_name"] } },
      )
    }
  }
}
