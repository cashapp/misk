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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = true)
class MySQLEnforceWritableConnectionsTest {
  @MiskTestModule
  val module = MySQLEnforceWritableConnectionsTestModule(appName = "test_mysql_enforce_writable_connections")

  @Inject @Movies lateinit var validatingTransacter: Transacter
  @Inject @Movies2 lateinit var transacter: Transacter

  @BeforeEach
  fun setUp() {
    transacter.transactionWithSession { (connection) -> setGlobalReadOnly(connection, 0) }
  }

  @AfterEach
  fun tearDown() {
    transacter.transactionWithSession { (connection) -> setGlobalReadOnly(connection, 0) }
  }

  @Nested
  @DisplayName("when using the validating transacter")
  inner class ValidatingTransacterTest {
    @Test
    fun `marks connections invalid if the DB instance becomes read only`() {
      validatingTransacter.transactionWithSession { (connection) ->
        setGlobalReadOnly(connection, 1)
        assertThat(connection.isValid(VALID_CHECK_TIMEOUT_SECONDS)).isFalse()

        setGlobalReadOnly(connection, 0)
        assertThat(connection.isValid(VALID_CHECK_TIMEOUT_SECONDS)).isTrue()
      }
    }
  }

  @Nested
  @DisplayName("when using the transacter")
  inner class TransacterTest {
    @Test
    fun `it does not mark connections invalid if the DB instance becomes read only`() {
      transacter.transactionWithSession { (connection) ->
        setGlobalReadOnly(connection, 1)
        assertThat(connection.isValid(VALID_CHECK_TIMEOUT_SECONDS)).isTrue()
      }
    }
  }

  companion object {
    private const val VALID_CHECK_TIMEOUT_SECONDS = 5
  }

  private fun setGlobalReadOnly(connection: Connection, state: Int) {
    connection.createStatement().executeUpdate("SET GLOBAL read_only = $state")
    connection.createStatement().executeUpdate("SET GLOBAL super_read_only = $state")
  }

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val mysql_enforce_writable_connections_data_source: DataSourceConfig,
  ) : Config

  class MySQLEnforceWritableConnectionsTestModule(private val appName: String) : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(FakeClockModule(), MockTracingBackendModule()))
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>(appName, TESTING)

      install(JdbcTestingModule(Movies::class))
      install(JdbcModule(Movies::class, config.mysql_enforce_writable_connections_data_source))

      install(JdbcTestingModule(Movies2::class))
      install(JdbcModule(Movies2::class, config.mysql_data_source))
    }
  }
}
