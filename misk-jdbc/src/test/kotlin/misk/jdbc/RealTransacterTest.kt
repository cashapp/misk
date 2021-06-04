package misk.jdbc

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import java.sql.Connection
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertFailsWith
import kotlin.test.fail

abstract class RealTransacterTest {
  @Inject @Movies lateinit var transacter: Transacter

  @Test
  fun happyPathWithConnection() {
    createTestData()
    val actual = transacter.transaction { connection ->
      connection.createStatement()
        .executeQuery("SELECT name, created_at FROM movies ORDER BY created_at ASC")
        .map {
          Movie(it.getString(1), it.getDate(2).toLocalDate())
        }
    }
    val expected = listOf(
      Movie("Star Wars", LocalDate.of(1977, 5, 25)),
      Movie("Luxo Jr.", LocalDate.of(1986, 8, 17)),
      Movie("Jurassic Park", LocalDate.of(1993, 6, 9)),
    )
    assertThat(actual).containsExactlyElementsOf(expected)
  }

  @Test
  fun happyPath() {
    createTestData()

    val actual = transacter.transactionWithSession { (connection) ->
      connection.createStatement()
        .executeQuery("SELECT name, created_at FROM movies ORDER BY created_at ASC")
        .map {
          Movie(it.getString(1), it.getDate(2).toLocalDate())
        }
    }

    val expected = listOf(
      Movie("Star Wars", LocalDate.of(1977, 5, 25)),
      Movie("Luxo Jr.", LocalDate.of(1986, 8, 17)),
      Movie("Jurassic Park", LocalDate.of(1993, 6, 9)),
    )
    assertThat(actual).containsExactlyElementsOf(expected)
  }

  class BadException(message: String) : Exception(message)

  val count: (connection: Connection) -> Int = { connection ->
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT count(*) FROM movies").map {
        it.getInt(1)
      }[0]
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
        session.onPreCommit {
          preCommit1Executed = true
        }
        session.onPreCommit {
          preCommit2Executed = true
        }
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
          session.onPreCommit {
            throw RuntimeException()
          }
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
        session.onPostCommit {
          postCommit1Executed = true
        }
        session.onPostCommit {
          postCommit2Executed = true
        }
      }
    }

    assertThat(postCommit1Executed).isTrue
    assertThat(postCommit2Executed).isTrue
  }

  @Test
  fun `exception in post commit hook does not cause the transaction to rollback`() {
    assertThatExceptionOfType(PostCommitHookFailedException::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          session.onPostCommit {
            throw RuntimeException()
          }
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
        }
      }
    }
    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(1)
  }

  @Test
  fun `session close hooks execute when there are no exceptions`() {
    var sessionCloseHook1Executed = false
    var sessionCloseHook2Executed = false
    transacter.transactionWithSession { session ->
      session.useConnection {
        session.onSessionClose {
          sessionCloseHook1Executed = true
        }
        session.onSessionClose {
          sessionCloseHook2Executed = true
        }
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
          session.onSessionClose {
            sessionCloseHook1Executed = true
          }
          session.onPreCommit {
            throw RuntimeException()
          }
        }
      }
    }
    assertThat(sessionCloseHook1Executed).isTrue

    var sessionCloseHook2Executed = false
    assertThatExceptionOfType(PostCommitHookFailedException::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection {
          session.onSessionClose {
            sessionCloseHook2Executed = true
          }
          session.onPostCommit {
            throw RuntimeException()
          }
        }
      }
    }
    assertThat(sessionCloseHook2Executed).isTrue

    var sessionCloseHook3Executed = false
    assertThatExceptionOfType(Exception::class.java).isThrownBy {
      transacter.transactionWithSession { session ->
        session.useConnection { connection ->
          session.onSessionClose {
            sessionCloseHook3Executed = true
          }
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
          session.onSessionClose {
            throw RuntimeException()
          }
          connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
        }
      }
    }
    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(1)
  }

  @Test
  fun `a new transaction can be started in session close hook`() {
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

    val afterCount = transacter.transactionWithSession { session -> session.useConnection(count) }
    assertThat(afterCount).isEqualTo(2)
  }

  @Test
  fun cannotStartTransactionWhileInTransaction() {
    assertFailsWith<IllegalStateException> {
      transacter.transactionWithSession {
        transacter.transactionWithSession {
          fail("transaction in transaction")
        }
      }
    }
  }

  private fun createTestData() {
    // Insert some movies, characters and actors.
    transacter.transactionWithSession { session ->
      session.useConnection { connection ->
        connection.prepareStatement("INSERT INTO movies (name, created_at) VALUES (?, ?)")
          .use { statement ->
            val insertMovie = fun(movie: Movie) {
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
    val cockroachdb_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig,
  ) : Config

  class RealTransacterTestModule(private val type: DataSourceType) : KAbstractModule() {
    override fun configure() {
      install(
        Modules.override(MiskTestingServiceModule()).with(
          FakeClockModule(),
          MockTracingBackendModule()
        )
      )
      val env = Env(TESTING.name)
      install(DeploymentModule(TESTING, env))
      val config = MiskConfig.load<RootConfig>("test_transacter", env)
      val dataSourceConfig = selectDataSourceConfig(config)
      install(JdbcTestingModule(Movies::class))
      install(
        JdbcModule(Movies::class, dataSourceConfig)
      )
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
  @MiskTestModule
  val module = RealTransacterTestModule(DataSourceType.MYSQL)
}

@MiskTest(startService = true)
class TiDBRealTransacterTest : RealTransacterTest() {
  @MiskTestModule
  val module = RealTransacterTestModule(DataSourceType.TIDB)
}

@MiskTest(startService = true)
class PostgreSQLRealTransacterTest : RealTransacterTest() {
  @MiskTestModule
  val module = RealTransacterTestModule(DataSourceType.POSTGRESQL)
}

@MiskTest(startService = true)
class CockroachDbRealTransacterTest : RealTransacterTest() {
  @MiskTestModule
  val module = RealTransacterTestModule(DataSourceType.COCKROACHDB)
}
