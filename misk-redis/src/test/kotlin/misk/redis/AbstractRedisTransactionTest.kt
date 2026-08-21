package misk.redis

import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test

abstract class AbstractRedisTransactionTest : AbstractRedisTest() {
  @Test
  fun `transaction queues commands and resolves responses after commit`() {
    redis.zadd("book", 100.0, "old")
    lateinit var removed: Supplier<Long>
    lateinit var added: Supplier<Long>

    redis.transaction {
      removed = zremRangeByScore("book", Redis.ZRangeScoreMarker(100.0), Redis.ZRangeScoreMarker(100.0))
      added = zadd("book", 100.0, "new")

      assertFails { removed.get() }
      assertEquals(100.0, redis.zscore("book", "old"))
      assertNull(redis.zscore("book", "new"))
    }

    assertEquals(1L, removed.get())
    assertEquals(1L, added.get())
    assertNull(redis.zscore("book", "old"))
    assertEquals(100.0, redis.zscore("book", "new"))
  }

  @Test
  fun `transaction discards queued commands when the block fails`() {
    redis["transaction-key"] = "before".encodeUtf8()

    assertFailsWith<IllegalStateException> {
      redis.transaction {
        set("transaction-key", "after".encodeUtf8())
        error("boom")
      }
    }

    assertEquals("before".encodeUtf8(), redis["transaction-key"])
  }
}
