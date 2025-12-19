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
    assertThat(handler.adjudicate(SQLException("vtgate connection error")))
      .isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(handler.adjudicate(SQLException("", "42S02", 1146))).isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(handler.adjudicate(SQLException("", "42S02", 1147)))
      .isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)

    assertThat(handler.adjudicate(SQLException("", "hy000", 1105))).isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)

    assertThat(handler.adjudicate(SQLException("one of the vttablets: has turned into a pile of slag", "", 0)))
      .isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
  }
}
