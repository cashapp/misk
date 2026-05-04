package misk.jdbc

import com.zaxxer.hikari.SQLExceptionOverride
import java.sql.SQLException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VitessExceptionHandlerTest {
  @Test
  fun `matches exception`() {
    val handler = VitessExceptionHandler()
    assertThat(handler.adjudicate(SQLException("Key not found")))
      .isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)
    assertThat(handler.adjudicate(SQLException("Duplicate entry 'foo_bar'")))
      .isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)
    assertThat(handler.adjudicate(SQLException("vtgate connection error", "HY000", 1105)))
      .isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(handler.adjudicate(SQLException("", "42S02", 1146))).isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(handler.adjudicate(SQLException("", "42S02", 1147)))
      .isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)

    assertThat(handler.adjudicate(SQLException("", "hy000", 1105))).isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
  }

  @Test
  fun `does not evict on integrity constraint violations`() {
    val handler = VitessExceptionHandler()
    // SQLState 23000 = integrity constraint violation (duplicate key)
    assertThat(handler.adjudicate(SQLException("Duplicate entry 'foo' for key 'PRIMARY'", "23000", 1062)))
      .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT)
    // SQLState 23000 with connection in message — should still NOT evict
    assertThat(
        handler.adjudicate(
          SQLException(
            "vttablet: rpc error: code = AlreadyExists desc = Duplicate entry connection_id=123",
            "23000",
            1062,
          )
        )
      )
      .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT)
  }

  @Test
  fun `does not evict on data exceptions`() {
    val handler = VitessExceptionHandler()
    // SQLState 22001 = string data right truncation
    assertThat(handler.adjudicate(SQLException("Data too long for column", "22001", 1406)))
      .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT)
    // SQLState 22012 = division by zero
    assertThat(handler.adjudicate(SQLException("Division by 0", "22012", 1365)))
      .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT)
  }

  @Test
  fun `does not evict on syntax errors`() {
    val handler = VitessExceptionHandler()
    // SQLState 42000 = syntax error
    assertThat(handler.adjudicate(SQLException("You have an error in your SQL syntax near 'null'", "42000", 1064)))
      .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT)
  }

  @Test
  fun `does not evict on cardinality violations`() {
    val handler = VitessExceptionHandler()
    // SQLState 21S01 = column count mismatch
    assertThat(handler.adjudicate(SQLException("Column count doesn't match value count", "21S01", 1136)))
      .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT)
  }

  @Test
  fun `still evicts on real connection failures`() {
    val handler = VitessExceptionHandler()
    // Real connection failures come back as (HY000, 1105) from Vitess
    assertThat(handler.adjudicate(SQLException("vttablet: rpc error: connection refused", "HY000", 1105)))
      .isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(handler.adjudicate(SQLException("vttablet: rpc error: connection reset by peer", "HY000", 1105)))
      .isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(handler.adjudicate(SQLException("vttablet: broken pipe", "HY000", 1105)))
      .isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
  }

  @Test
  fun `does not evict on generic vttablet errors`() {
    val handler = VitessExceptionHandler()
    // Generic vttablet error without connection failure — should NOT evict anymore
    assertThat(
        handler.adjudicate(
          SQLException("vttablet: rpc error: code = FailedPrecondition desc = query not supported", "", 0)
        )
      )
      .isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)
  }
}
