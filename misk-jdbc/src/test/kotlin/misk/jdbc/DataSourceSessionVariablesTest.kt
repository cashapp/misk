package misk.jdbc

import com.google.inject.util.Modules
import jakarta.inject.Inject
import java.sql.Connection
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = true)
class DataSourceSessionVariablesTest {
  @MiskTestModule val module = SessionVariablesTestModule()

  @Inject @Movies lateinit var transacter: Transacter

  @Test
  fun `connections keep session variables from jdbc_url_query_parameters`() {
    transacter.transactionWithSession { (connection) ->
      assertThat(sessionVariable(connection, "max_execution_time")).isEqualTo("5400000")
      assertThat(sessionVariable(connection, "sql_mode")).isEqualTo("STRICT_TRANS_TABLES")
    }
  }

  @Test
  fun `connections still run in UTC`() {
    transacter.transactionWithSession { (connection) ->
      assertThat(sessionVariable(connection, "time_zone")).isEqualTo("+00:00")
    }
  }

  private fun sessionVariable(connection: Connection, name: String): String =
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT @@session.$name").use { resultSet ->
        resultSet.next()
        resultSet.getString(1)
      }
    }

  data class RootConfig(val data_source: DataSourceConfig) : Config

  class SessionVariablesTestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(FakeClockModule(), MockTracingBackendModule()))
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>("test_session_variables", TESTING)
      install(JdbcTestingModule(Movies::class))
      install(JdbcModule(Movies::class, config.data_source))
    }
  }
}
