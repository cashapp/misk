package misk.jdbc

import com.zaxxer.hikari.SQLExceptionOverride
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.SQLException

@MiskTest(startService = true)
class MySQLExceptionHandlerTest {

  @Inject
  lateinit var registry: CollectorRegistry

  @MiskTestModule
  val testingModule = MiskTestingServiceModule()

  @Test
  fun `matches exception`() {
    val handler = MySQLExceptionHandler(registry)
    val adjudicateResult = SQLExceptionOverride.Override.CONTINUE_EVICT

    val exception1 = SQLException("Key not found")
    assertThat(handler.adjudicate(exception1)).isEqualTo(adjudicateResult)
    assertThat(handler.errorCounter!!.labels(
      exception1.errorCode.toString(),
      "",
      exception1.message,
      adjudicateResult.name
    ).get()).isEqualTo(1.0)

    val exception2 = SQLException(
      "",
      "42S02",
      1147
    )
    assertThat(handler.adjudicate(exception2)).isEqualTo(adjudicateResult)
    assertThat(handler.errorCounter.labels(
      exception2.errorCode.toString(),
      exception2.sqlState,
      exception2.message,
      adjudicateResult.name
    ).get()).isEqualTo(1.0)
  }
}
