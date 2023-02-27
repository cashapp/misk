package misk.jdbc

import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest(startService = true)
class ScaleSafetyCheckTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @TestDatasource lateinit var transacter: Transacter
  @Inject @TestDatasource lateinit var dataSourceProvider: DataSourceService


  @BeforeEach
  internal fun setUp() {
    // Insert some data
    transacter.transactionWithSession { jdbcSession ->
      jdbcSession.useConnection { connection ->
        val statement = connection.createStatement()
        statement.execute("INSERT INTO movies (name) VALUES ('Star Wars')")
        statement.execute("INSERT INTO movies (name) VALUES ('Jurassic Park')")
      }
    }
  }

  @Test
  fun countingRecords() {
    val count = transacter.transactionWithSession { jdbcSession ->
      jdbcSession.useConnection { connection ->
        connection.createStatement().use { statement ->
          statement.executeQuery("SELECT count(*) FROM movies").map {
            it.getInt(1)
          }[0]
        }
      }
    }
    assertThat(count).isEqualTo(2)
  }

  @Test
  fun `table scan throws`() {
    assertThatThrownBy {
      transacter.transactionWithSession { jdbSession ->
        jdbSession.useConnection { connection ->
          connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id from movies where name = 'Star Wars'")
          }
        }
      }
    }.isInstanceOf(TableScanException::class.java)
      .hasMessageContaining("Missing index on query:")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())

      val config = MiskConfig.load<TestConfig>("test_truncatetables_app", TESTING)
      install(JdbcModule(TestDatasource::class, config.data_source))
      install(JdbcTestingModule(qualifier = TestDatasource::class, scaleSafetyChecks = true))
    }
  }

  data class TestConfig(val data_source: DataSourceConfig) : Config

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class TestDatasource
}

