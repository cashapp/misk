package misk.jdbc

import com.google.inject.util.Providers
import com.squareup.moshi.Moshi
import misk.MiskServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.hibernate.Transacter
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.hibernate.query.Query
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest(startService = true)
internal class TruncateTablesServiceTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @TestDatasource lateinit var config: DataSourceConfig
  @Inject @TestDatasource lateinit var sessionFactory: SessionFactory
  @Inject @TestDatasource lateinit var transacter: Transacter

  // Just a dummy
  val vitessScatterDetector: VitessScaleSafetyChecks
    get() = VitessScaleSafetyChecks(
        OkHttpClient(),
        Moshi.Builder().build(),
        config,
        StartVitessService(config))

  @BeforeEach
  internal fun setUp() {
    // Manually truncate because we don't use TruncateTablesService to test itself!
    sessionFactory.openSession().use { session ->
      session.doReturningWork { connection ->
        val statement = connection.createStatement()
        statement.addBatch("DELETE FROM movies")
        statement.executeBatch()
      }
    }
  }

  @Test
  fun truncateUserTables() {
    assertThat(rowCount("schema_version")).isGreaterThan(0)
    assertThat(rowCount("movies")).isEqualTo(0)

    // Insert some data.
    sessionFactory.openSession().doWork { connection ->
      connection.createStatement().use { statement ->
        statement.execute("INSERT INTO movies (name) VALUES ('Star Wars')")
        statement.execute("INSERT INTO movies (name) VALUES ('Jurassic Park')")
      }
    }
    assertThat(rowCount("schema_version")).isGreaterThan(0)
    assertThat(rowCount("movies")).isGreaterThan(0)

    // Start up TruncateTablesService. The inserted data should be truncated.
    val service = TruncateTablesService(TestDatasource::class, config,
        Providers.of(transacter),
        vitessScatterDetector)
    service.startAsync()
    service.awaitRunning()
    assertThat(rowCount("schema_version")).isGreaterThan(0)
    assertThat(rowCount("movies")).isEqualTo(0)
  }

  @Test
  fun startUpStatements() {
    val service = TruncateTablesService(
        TestDatasource::class,
        config,
        Providers.of(transacter),
        vitessScatterDetector,
        startUpStatements = listOf("INSERT INTO movies (name) VALUES ('Star Wars')"))

    assertThat(rowCount("movies")).isEqualTo(0)
    service.startAsync()
    service.awaitRunning()
    assertThat(rowCount("movies")).isGreaterThan(0)
  }

  @Test
  fun shutDownStatements() {
    val service = TruncateTablesService(
        TestDatasource::class,
        config,
        Providers.of(transacter),
        vitessScatterDetector,
        shutDownStatements = listOf("INSERT INTO movies (name) VALUES ('Star Wars')"))

    service.startAsync()
    service.awaitRunning()

    assertThat(rowCount("movies")).isEqualTo(0)
    service.stopAsync()
    service.awaitTerminated()
    assertThat(rowCount("movies")).isGreaterThan(0)
  }

  private fun rowCount(table: String): Int {
    sessionFactory.openSession().use { session ->
      @Suppress("UNCHECKED_CAST") // createNativeQuery returns a raw Query.
      val query = session.createNativeQuery("SELECT count(*) FROM $table") as Query<Number>
      return query.list()[0].toInt()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      bind<Environment>().toInstance(Environment.TESTING)
      install(MiskServiceModule())

      val config = MiskConfig.load<TestConfig>("test_truncatetables_app", Environment.TESTING)
      install(HibernateModule(
          TestDatasource::class, config.data_source))
      install(object : HibernateEntityModule(
          TestDatasource::class) {
        override fun configureHibernate() {
        }
      })
    }
  }

  data class TestConfig(val data_source: DataSourceConfig) : Config

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class TestDatasource
}
