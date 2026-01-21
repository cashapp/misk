package misk.jdbc

import com.google.inject.util.Modules
import jakarta.inject.Inject
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDate
import kotlin.test.assertFailsWith
import kotlin.test.fail
import misk.MiskTestingServiceModule
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.deployment.TESTING

abstract class RealTransacterTest {
  @Inject @Movies lateinit var transacter: Transacter

  @Test
  fun happyPathWithConnection() {
    createTestData()
    val actual =
      transacter.transaction { connection ->
        connection.createStatement().executeQuery("SELECT name, created_at FROM movies ORDER BY created_at ASC").map {
          Movie(it.getString(1), it.getDate(2).toLocalDate())
        }
      }
    val expected =
      listOf(
        Movie("Star Wars", LocalDate.of(1977, 5, 25)),
        Movie("Luxo Jr.", LocalDate.of(1986, 8, 17)),
        Movie("Jurassic Park", LocalDate.of(1993, 6, 9)),
      )
    assertThat(actual).containsExactlyElementsOf(expected)
  }

  @Test
  fun happyPath() {
    createTestData()

    val actual =
      transacter.transactionWithSession { (connection) ->
        connection.createStatement().executeQuery("SELECT name, created_at FROM movies ORDER BY created_at ASC").map {
          Movie(it.getString(1), it.getDate(2).toLocalDate())
        }
      }

    val expected =
      listOf(
        Movie("Star Wars", LocalDate.of(1977, 5, 25)),
        Movie("Luxo Jr.", LocalDate.of(1986, 8, 17)),
        Movie("Jurassic Park", LocalDate.of(1993, 6, 9)),
      )
    assertThat(actual).containsExactlyElementsOf(expected)
  }

  class BadException(message: String) : Exception(message)

  val count: (connection: Connection) -> Int = { connection ->
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT count(*) FROM movies").map { it.getInt(1) }[0]
    }
  }

  @Test
  fun exceptionCausesTransactionToRollback() {
    val beforeCount = transacter.transactionWithSession { session -> session.useConnection(count) }

    assertFailsWith<BadException> {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
          assertThat(count(connection)).isEqualTo(beforeCount + 1)
          throw BadException("boom!")
        }
      }
    }

    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(beforeCount)
  }

  @Test
  fun preCommitHooksExecute() {
    var preCommit1Executed = false
    var preCommit2Executed = false
    transacter.transactionWithSession { session ->
      session.useConnection {
        session.onPreCommit { preCommit1Executed = true }
        session.onPreCommit { preCommit2Executed = true }
      }
    }

    assertThat(preCommit1Executed).isTrue
    assertThat(preCommit2Executed).isTrue
  }

  @Test
  fun `exception in pre commit hook causes the transaction to rollback`() {
    assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          session.onPreCommit { throw RuntimeException() }
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
        }
      }
    }
    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(0)
  }

  @Test
  fun postCommitHooksExecute() {
    var postCommit1Executed = false
    var postCommit2Executed = false
    transacter.transactionWithSession { session ->
      session.useConnection {
        session.onPostCommit { postCommit1Executed = true }
        session.onPostCommit { postCommit2Executed = true }
      }
    }

    assertThat(postCommit1Executed).isTrue
    assertThat(postCommit2Executed).isTrue
  }

  @Test
  fun `exception in post commit hook does not cause the transaction to rollback`() {
    retry(RetryConfig.Builder(3, FlatBackoff()).shouldRetry { it.isCockroachDbRetryableError() }.build()) {
      assertThatExceptionOfType(PostCommitHookFailedException::class.java).isThrownBy {
        transacter.transactionWithSession { session ->
          session.useConnection { connection ->
            session.onPostCommit { throw RuntimeException() }
            connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
          }
        }
      }
    }

    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(1)
  }

  @Test
  fun rollbackHooksCalledOnRollbackOnly() {
    val rollbackHooksTriggered = mutableListOf<String>()

    // Happy path.
    transacter.transactionWithSession { session ->
      session.onRollback { _ ->
        rollbackHooksTriggered.add("never")
        error("this should never have happened")
      }
    }

    assertThat(rollbackHooksTriggered).isEmpty()

    // Rollback path.
    assertThrows<IllegalStateException> {
      transacter.transactionWithSession { session ->
        session.onRollback { error ->
          assertThat(error).hasMessage("bad things happened here")
          assertThat(transacter.inTransaction).isFalse
          rollbackHooksTriggered.add("first")
        }
        session.onRollback { error ->
          assertThat(error).hasMessage("bad things happened here")
          assertThat(transacter.inTransaction).isFalse
          rollbackHooksTriggered.add("second")
        }
        error("bad things happened here")
      }
    }
    assertThat(rollbackHooksTriggered).containsExactly("first", "second")
  }

  @Test
  fun `session close hooks execute when there are no exceptions`() {
    var sessionCloseHook1Executed = false
    var sessionCloseHook2Executed = false
    transacter.transactionWithSession { session ->
      session.useConnection {
        session.onSessionClose { sessionCloseHook1Executed = true }
        session.onSessionClose { sessionCloseHook2Executed = true }
      }
    }
    assertThat(sessionCloseHook1Executed).isTrue
    assertThat(sessionCloseHook2Executed).isTrue
  }

  @Test
  fun `session close hooks always execute regardless of when an exception is thrown`() {
    var sessionCloseHook1Executed = false
    assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection {
          session.onSessionClose { sessionCloseHook1Executed = true }
          session.onPreCommit { throw RuntimeException() }
        }
      }
    }
    assertThat(sessionCloseHook1Executed).isTrue

    var sessionCloseHook2Executed = false
    assertThatExceptionOfType(PostCommitHookFailedException::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection {
          session.onSessionClose { sessionCloseHook2Executed = true }
          session.onPostCommit { throw RuntimeException() }
        }
      }
    }
    assertThat(sessionCloseHook2Executed).isTrue

    var sessionCloseHook3Executed = false
    assertThatExceptionOfType(Exception::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          session.onSessionClose { sessionCloseHook3Executed = true }
          // force a sql exception by storing null for a non nullable column
          connection.createStatement().execute("INSERT INTO movies (name) VALUES (null)")
        }
      }
    }
    assertThat(sessionCloseHook3Executed).isTrue
  }

  @Test
  fun `session close hook can throw an exception too but does not rollback the transaction`() {
    assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          session.onSessionClose { throw RuntimeException() }
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
        }
      }
    }
    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(1)
  }

  @Test
  fun `a new transaction can be started in session close hook`() {
    retry(RetryConfig.Builder(3, FlatBackoff()).shouldRetry { it.isCockroachDbRetryableError() }.build()) {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          session.onSessionClose {
            transacter.transactionWithSession { innerSession ->
              innerSession.useConnection { innerConnection ->
                innerConnection.createStatement().execute("INSERT INTO movies (name) VALUES ('1')")
              }
            }
          }
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('2')")
        }
      }
    }

    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(2)
  }

  private fun Exception.isCockroachDbRetryableError(): Boolean {
    return this is SQLException && sqlState == "40001" // CockroachDB's serializable transaction error code
  }

  @Test
  fun cannotStartTransactionWhileInTransaction() {
    assertFailsWith<IllegalStateException> {
      transacter.transactionWithSession { transacter.transactionWithSession { fail("transaction in transaction") } }
    }
  }

  @Test
  fun `TransactionIsolationLevel enum has correct JDBC values`() {
    // Test enum values match JDBC constants
    assertThat(TransactionIsolationLevel.READ_UNCOMMITTED.jdbcValue).isEqualTo(1)
    assertThat(TransactionIsolationLevel.READ_COMMITTED.jdbcValue).isEqualTo(2)
    assertThat(TransactionIsolationLevel.REPEATABLE_READ.jdbcValue).isEqualTo(4)
    assertThat(TransactionIsolationLevel.SERIALIZABLE.jdbcValue).isEqualTo(8)
  }

  @Test
  fun `TransactionOptions with no isolation level uses database default`() {
    val options = TransactionOptions()
    assertThat(options.isolationLevel).isNull()

    // Should work without setting any isolation level
    transacter.transactionWithSession(options) { session ->
      session.useConnection { connection ->
        // Just verify it works - database default isolation will be used
        val result = connection.createStatement().executeQuery("SELECT 1").apply { next() }.getInt(1)
        assertThat(result).isEqualTo(1)
      }
    }
  }

  @Test
  fun `transactionWithSession with TransactionOptions sets isolation level`() {
    val options = TransactionOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)

    transacter.transactionWithSession(options) { session ->
      session.useConnection { connection ->
        // Verify the isolation level was set correctly
        assertThat(connection.transactionIsolation).isEqualTo(TransactionIsolationLevel.READ_COMMITTED.jdbcValue)
      }
    }
  }

  @Test
  fun `different isolation levels can be set for different transactions`() {
    // Test READ_COMMITTED (widely supported)
    transacter.transactionWithSession(
      TransactionOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)
    ) { session ->
      session.useConnection { connection ->
        assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
      }
    }

    // Test REPEATABLE_READ (widely supported)
    transacter.transactionWithSession(
      TransactionOptions(isolationLevel = TransactionIsolationLevel.REPEATABLE_READ)
    ) { session ->
      session.useConnection { connection ->
        assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ)
      }
    }

    // Test SERIALIZABLE (may not be supported by all databases like TiDB)
    try {
      transacter.transactionWithSession(
        TransactionOptions(isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
      ) { session ->
        session.useConnection { connection ->
          assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_SERIALIZABLE)
        }
      }
    } catch (e: SQLException) {
      // Some databases (like TiDB) don't support SERIALIZABLE isolation level
      // This is expected behavior - the API should still work and throw a clear error
      assertThat(e.message).contains("The isolation level 'SERIALIZABLE' is not supported")
    }
  }

  @Test
  fun `backward compatibility - transactionWithSession without options still works`() {
    // Ensure existing method without options continues to work
    transacter.transactionWithSession { session ->
      session.useConnection { connection ->
        val result = connection.createStatement().executeQuery("SELECT 1").apply { next() }.getInt(1)
        assertThat(result).isEqualTo(1)
      }
    }
  }

  @Test
  fun `isolation level setting works with hooks`() {
    var preCommitExecuted = false
    var postCommitExecuted = false

    transacter.transactionWithSession(
      TransactionOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)
    ) { session ->
      session.useConnection { connection ->
        // Verify isolation level is set
        assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)

        // Add hooks to ensure they still work with isolation level setting
        session.onPreCommit { preCommitExecuted = true }
        session.onPostCommit { postCommitExecuted = true }

        // Insert some data
        connection.createStatement().execute("INSERT INTO movies (name) VALUES ('Test Movie')")
      }
    }

    assertThat(preCommitExecuted).isTrue
    assertThat(postCommitExecuted).isTrue
  }

  @Test
  fun `isolation level exception handling works correctly`() {
    assertFailsWith<BadException> {
      transacter.transactionWithSession(
        TransactionOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)
      ) { session ->
        session.useConnection { connection ->
          assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('Test Movie')")
          throw BadException("test exception")
        }
      }
    }

    // Verify transaction was rolled back despite having isolation level set
    val count = transacter.transactionWithSession { session ->
      session.useConnection(this.count)
    }
    assertThat(count).isEqualTo(0) // Should be 0 if properly rolled back
  }

  private fun createTestData() {
    // Insert some movies, characters and actors.
    transacter.transactionWithSession { session ->
      session.useConnection { connection ->
        connection.prepareStatement("INSERT INTO movies (name, created_at) VALUES (?, ?)").use { statement ->
          val insertMovie =
            fun(movie: Movie) {
              statement.setString(1, movie.name)
              statement.setObject(2, movie.date)
              statement.addBatch()
            }
          insertMovie(Movie("Jurassic Park", LocalDate.of(1993, 6, 9)))
          insertMovie(Movie("Star Wars", LocalDate.of(1977, 5, 25)))
          insertMovie(Movie("Luxo Jr.", LocalDate.of(1986, 8, 17)))
          statement.executeBatch()
        }
      }
    }
  }

  data class Movie(val name: String, val date: LocalDate)

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val mysql_enforce_writable_connections_data_source: DataSourceConfig,
    val cockroachdb_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig,
  ) : Config

  class RealTransacterTestModule(
    private val type: DataSourceType,
    private val dataSourceConfig: DataSourceConfig? = null,
  ) : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(FakeClockModule(), MockTracingBackendModule()))
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>("test_transacter", TESTING)
      install(JdbcTestingModule(Movies::class))
      install(JdbcModule(Movies::class, dataSourceConfig ?: selectDataSourceConfig(config)))
    }

    private fun selectDataSourceConfig(config: RootConfig): DataSourceConfig {
      return when (type) {
        DataSourceType.MYSQL -> config.mysql_data_source
        DataSourceType.COCKROACHDB -> config.cockroachdb_data_source
        DataSourceType.POSTGRESQL -> config.postgresql_data_source
        DataSourceType.TIDB -> config.tidb_data_source
        else -> throw Exception("data source not supported: $type")
      }
    }
  }
}

@MiskTest(startService = true)
class MySQLRealTransacterTest : RealTransacterTest() {
  @MiskTestModule val module = RealTransacterTestModule(DataSourceType.MYSQL)
}

@MiskTest(startService = true)
class MySQLEnforceWritableConnectionsTransacterTest : RealTransacterTest() {
  @MiskTestModule
  val module =
    RealTransacterTestModule(
      DataSourceType.MYSQL,
      MiskConfig.load<RootConfig>("test_transacter", TESTING).mysql_enforce_writable_connections_data_source,
    )
}

@MiskTest(startService = true)
class TiDBRealTransacterTest : RealTransacterTest() {
  @MiskTestModule val module = RealTransacterTestModule(DataSourceType.TIDB)
}

@MiskTest(startService = true)
class PostgreSQLRealTransacterTest : RealTransacterTest() {
  @MiskTestModule val module = RealTransacterTestModule(DataSourceType.POSTGRESQL)
}

@MiskTest(startService = true)
@Disabled(value = "Requires the ExternalDependency implementation to be less flakey")
class CockroachDbRealTransacterTest : RealTransacterTest() {
  @MiskTestModule val module = RealTransacterTestModule(DataSourceType.COCKROACHDB)
}
