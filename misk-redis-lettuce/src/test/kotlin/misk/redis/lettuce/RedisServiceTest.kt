package misk.redis.lettuce

import kotlin.test.assertFailsWith
import misk.redis.lettuce.standalone.redisClient
import org.junit.jupiter.api.Test

internal class RedisServiceTest {
  @Test
  fun `startup fails when an essential Redis is unreachable`() {
    val client =
      redisClient(
        redisUri {
          withHost("127.0.0.1")
          withPort(TcpProxy.freePort())
        }
      )
    try {
      val service =
        RedisService(clients = setOf(client), connectionProviders = emptySet(), functionCodeLoaders = emptySet())
      assertFailsWith<IllegalStateException> { service.startAsync().awaitRunning() }
    } finally {
      client.shutdown()
    }
  }
}
