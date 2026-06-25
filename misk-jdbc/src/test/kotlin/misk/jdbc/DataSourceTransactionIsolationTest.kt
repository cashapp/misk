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
class DataSourceTransactionIsolationTest {
  @MiskTestModule val module = TransactionIsolationTestModule(appName = "test_transaction_isolation")

  @Inject @Movies lateinit var readCommittedTransacter: Transacter
  @Inject @Movies2 lateinit var defaultTransacter: Transacter
  @Inject @Movies3 lateinit var perTransactionTransacter: Transacter

  @Test
  fun `connections use the configured transaction isolation`() {
    readCommittedTransacter.transactionWithSession { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
      assertThat(serverSessionIsolation(connection)).isEqualTo("READ-COMMITTED")
    }
  }

  @Test
  fun `connections use the server default when no isolation is configured`() {
    defaultTransacter.transactionWithSession { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ)
      assertThat(serverSessionIsolation(connection)).isEqualTo("REPEATABLE-READ")
    }
  }

  @Test
  fun `connections whose isolation was changed at runtime are reset to the configured level on checkin`() {
    // The read-committed pool is configured with a single connection, so the connection dirtied here is
    // deterministically the one every subsequent transaction checks out.
    readCommittedTransacter.transactionWithSession { (connection) ->
      connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
      assertThat(serverSessionIsolation(connection)).isEqualTo("REPEATABLE-READ")
    }

    readCommittedTransacter.transactionWithSession { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
      assertThat(serverSessionIsolation(connection)).isEqualTo("READ-COMMITTED")
    }
  }

  @Test
  fun `per-transaction isolation is applied for the call on a pool with no configured isolation`() {
    perTransactionTransacter.transactionWithSession(
      TransactionOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)
    ) { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
      assertThat(serverSessionIsolation(connection)).isEqualTo("READ-COMMITTED")
    }
  }

  @Test
  fun `per-transaction isolation is restored on return and does not leak to later transactions`() {
    // This pool has no transaction_isolation configured, so Hikari's reset-on-return never fires. It is also a single
    // connection, so the connection used below is deterministically reused by the following transaction. This proves
    // the transacter itself captures and restores the prior level rather than relying on the pool.
    perTransactionTransacter.transactionWithSession(
      TransactionOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)
    ) { (connection) ->
      assertThat(serverSessionIsolation(connection)).isEqualTo("READ-COMMITTED")
    }

    perTransactionTransacter.transactionWithSession { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ)
      assertThat(serverSessionIsolation(connection)).isEqualTo("REPEATABLE-READ")
    }
  }

  @Test
  fun `transaction without options leaves the connection at the server default`() {
    perTransactionTransacter.transactionWithSession(TransactionOptions()) { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ)
      assertThat(serverSessionIsolation(connection)).isEqualTo("REPEATABLE-READ")
    }
  }

  @Test
  fun `per-transaction isolation overrides a pool-configured level and restores it afterwards`() {
    // The read-committed pool defaults each connection to READ COMMITTED. A single transaction can opt into a stricter
    // level, and the connection returns to the pool's configured level for the next borrower.
    readCommittedTransacter.transactionWithSession(
      TransactionOptions(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
    ) { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_SERIALIZABLE)
      assertThat(serverSessionIsolation(connection)).isEqualTo("SERIALIZABLE")
    }

    readCommittedTransacter.transactionWithSession { (connection) ->
      assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
      assertThat(serverSessionIsolation(connection)).isEqualTo("READ-COMMITTED")
    }
  }

  private fun serverSessionIsolation(connection: Connection): String =
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT @@transaction_isolation").use { resultSet ->
        resultSet.next()
        resultSet.getString(1)
      }
    }

  data class RootConfig(
    val default_isolation_data_source: DataSourceConfig,
    val read_committed_data_source: DataSourceConfig,
    val per_transaction_data_source: DataSourceConfig,
  ) : Config

  class TransactionIsolationTestModule(private val appName: String) : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(FakeClockModule(), MockTracingBackendModule()))
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>(appName, TESTING)

      install(JdbcTestingModule(Movies::class))
      install(JdbcModule(Movies::class, config.read_committed_data_source))

      install(JdbcTestingModule(Movies2::class))
      install(JdbcModule(Movies2::class, config.default_isolation_data_source))

      install(JdbcTestingModule(Movies3::class))
      install(JdbcModule(Movies3::class, config.per_transaction_data_source))
    }
  }
}
