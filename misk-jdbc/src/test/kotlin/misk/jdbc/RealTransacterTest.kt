package misk.jdbc

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException
import java.sql.Connection
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertFailsWith
import kotlin.test.fail

abstract class RealTransacterTest {
  @Inject @Movies lateinit var transacter: Transacter

  @Test
  fun happyPath() {
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

  class BadException(message: String) : Exception(message)

  @Test
  fun exceptionCausesTransactionToRollback() {
    val count: (connection: Connection) -> Int = { connection ->
      connection.createStatement().use { statement ->
        statement.executeQuery("SELECT count(*) FROM movies").map {
          it.getInt(1)
        }[0]
      }
    }
    val beforeCount = transacter.transaction { count(it) }

    assertFailsWith<BadException> {
      transacter.transaction { connection ->
        connection.createStatement().execute("INSERT INTO movies (name) VALUES ('hello')")
        assertThat(count(connection)).isEqualTo(beforeCount + 1)
        throw BadException("boom!")
      }
    }

    val afterCount = transacter.transaction { count(it) }
    assertThat(afterCount).isEqualTo(beforeCount)
  }

  @Test
  fun cannotStartTransactionWhileInTransaction() {
    assertFailsWith<IllegalStateException> {
      transacter.transaction {
        transacter.transaction {
          fail("transaction in transaction")
        }
      }
    }
  }

  private fun createTestData() {
    // Insert some movies, characters and actors.
    transacter.transaction { connection ->
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
      install(DeploymentModule.forTesting())
      val config = MiskConfig.load<RootConfig>("test_transacter", Environment.TESTING)
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
