package misk.jdbc

import java.sql.SQLException
import com.zaxxer.hikari.SQLExceptionOverride
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VitessExceptionHandlerTest {
  private val handler = VitessExceptionHandler()

  @Test
  fun duplicateKeyErrorDoesNotEvict() {
    val exception = SQLException(
      "target: keyspace.shard.primary: vttablet: rpc error: code = AlreadyExists " +
        "desc = Duplicate entry 'foo' for key 'PRIMARY' (errno 1062) (sqlstate 23000)",
      "23000",
      1062,
    )
    assertEquals(SQLExceptionOverride.Override.MUST_NOT_EVICT, handler.adjudicate(exception))
  }

  @Test
  fun deadlockErrorDoesNotEvict() {
    val exception = SQLException(
      "target: keyspace.shard.primary: vttablet: rpc error: code = Aborted " +
        "desc = Deadlock found when trying to get lock (errno 1213) (sqlstate 40001)",
      "40001",
      1213,
    )
    assertEquals(SQLExceptionOverride.Override.MUST_NOT_EVICT, handler.adjudicate(exception))
  }

  @Test
  fun connectionErrorEvicts() {
    val exception = SQLException(
      "target: keyspace.shard.primary: vttablet: rpc error: code = Unavailable " +
        "desc = connection refused (errno 2002) (sqlstate HY000)",
      "HY000",
      2002,
    )
    assertEquals(SQLExceptionOverride.Override.MUST_EVICT, handler.adjudicate(exception))
  }

  @Test
  fun knownBadStateEvicts() {
    val exception = SQLException(
      "target: keyspace.shard.primary: vttablet: table not found",
      "42S02",
      1146,
    )
    assertEquals(SQLExceptionOverride.Override.MUST_EVICT, handler.adjudicate(exception))
  }

  @Test
  fun unknownErrorWithoutBadPatternContinuesEvict() {
    val exception = SQLException(
      "some other error without bad patterns",
      "HY000",
      9999,
    )
    assertEquals(SQLExceptionOverride.Override.CONTINUE_EVICT, handler.adjudicate(exception))
  }
}
