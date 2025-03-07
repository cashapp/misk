package misk.vitess

import com.zaxxer.hikari.SQLExceptionOverride
import org.junit.jupiter.api.Test
import java.sql.SQLException
import org.assertj.core.api.Assertions.assertThat

class VitessExceptionHandlerTest {
  @Test
  fun `matches exception`() {
    val handler = VitessExceptionHandler()
    assertThat(handler.adjudicate(SQLException("Key not found"))).isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)
    assertThat(handler.adjudicate(SQLException("Duplicate entry 'foo_bar'"))).isEqualTo(
      SQLExceptionOverride.Override.CONTINUE_EVICT
    )
    assertThat(handler.adjudicate(SQLException("vtgate connection error"))).isEqualTo(
      SQLExceptionOverride.Override.MUST_EVICT
    )
    assertThat(
      handler.adjudicate(
        SQLException(
          "",
          "42S02",
          1146
        )
      )
    ).isEqualTo(SQLExceptionOverride.Override.MUST_EVICT)
    assertThat(
      handler.adjudicate(
        SQLException(
          "",
          "42S02",
          1147
        )
      )
    ).isEqualTo(SQLExceptionOverride.Override.CONTINUE_EVICT)
  }
}
