package misk.jdbc

import com.google.common.util.concurrent.Service
import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.name.Names
import misk.MiskModule
import misk.config.Config
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.keyOf
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hsqldb.jdbc.JDBCDataSource
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.SQLInvalidAuthorizationSpecException
import javax.inject.Provider
import javax.inject.Qualifier
import javax.sql.DataSource

@MiskTest(startService = true)
internal class DataSourceModuleTest {
  val defaultEnv = Environment.TESTING
  val rootConfig = MiskConfig.load<RootConfig>("test_data_source_app", defaultEnv)

  @MiskTestModule
  val testModule = object : KAbstractModule() {
    override fun configure() {
      install(MiskModule())

      val config: DataSourceConfig = rootConfig.data_source_clusters["exemplar"]!!.writer
      val hsqlService = InMemoryHsqlService(config,
          setUpStatements = listOf(
              PeopleDatabase.DROP_TABLE_PEOPLE,
              PeopleDatabase.CREATE_TABLE_PEOPLE,
              "INSERT INTO people(id, name) VALUES(100, 'Mary')",
              "INSERT INTO people(id, name) VALUES(101, 'Phil')"
          ),
          tearDownStatements = listOf(
              PeopleDatabase.DROP_TABLE_PEOPLE
          )
      )
      binder().addMultibinderBinding<Service>().toInstance(hsqlService)
      bind<JDBCDataSource>().toProvider(Provider<JDBCDataSource> { hsqlService.datasource })
    }
  }

  @Test fun bindsDataSourceWithoutAnnotation() {
    val injector = Guice.createInjector(
        DataSourceModule.create("exemplar"),
        ConfigModule.create("my-app", rootConfig))

    val dataSourceCluster = injector.getInstance(keyOf<DataSourceCluster>())
    val db = PeopleDatabase(dataSourceCluster)
    val results = db.listPeople()
    assertThat(results).containsExactly(100 to "Mary", 101 to "Phil")

    val dataSource = injector.getInstance(keyOf<DataSource>())
    assertSame(dataSource, dataSourceCluster.writer)
  }

  @Test fun bindsDataSourceWithAnnotationInstance() {
    val injector = Guice.createInjector(
        DataSourceModule.create("exemplar", Names.named("exemplar")),
        ConfigModule.create("my-app", rootConfig))

    val dataSourceCluster = injector.getInstance(keyOf<DataSourceCluster>(Names.named("exemplar")))
    val db = PeopleDatabase(dataSourceCluster)
    val results = db.listPeople()
    assertThat(results).containsExactly(100 to "Mary", 101 to "Phil")

    val dataSource = injector.getInstance(keyOf<DataSource>(Names.named("exemplar")))
    assertSame(dataSource, dataSourceCluster.writer)
  }

  @Test fun bindsDataSourceWithAnnotation() {
    val injector = Guice.createInjector(
        DataSourceModule.create("exemplar", Exemplar::class),
        ConfigModule.create("my-app", rootConfig))

    val dataSourceCluster = injector.getInstance(keyOf(DataSourceCluster::class, Exemplar::class))
    val db = PeopleDatabase(dataSourceCluster)
    val results = db.listPeople()
    assertThat(results).containsExactly(100 to "Mary", 101 to "Phil")

    val dataSource = injector.getInstance(keyOf(DataSource::class, Exemplar::class))
    assertSame(dataSource, dataSourceCluster.writer)
  }

  @Test fun usesProperUsername() {
    val exception = assertThrows(CreationException::class.java) {
      Guice.createInjector(
          DataSourceModule.create("exemplar-incorrect-username"),
          ConfigModule.create("my-app", rootConfig)
      )
    }
    val error = exception.errorMessages.first().cause!!
    assertThat(error.cause).isInstanceOf(SQLInvalidAuthorizationSpecException::class.java)
    assertThat(error.message).contains("not found: INCORRECT_USERNAME")
  }

  @Test fun usesProperPassword() {
    val exception = assertThrows(CreationException::class.java) {
      Guice.createInjector(
          DataSourceModule.create("exemplar-incorrect-password"),
          ConfigModule.create("my-app", rootConfig)
      )
    }
    val error = exception.errorMessages.first().cause!!
    assertThat(error.cause).isInstanceOf(SQLInvalidAuthorizationSpecException::class.java)
  }

  @Test fun failsIfDataSourceNotFound() {
    val exception = assertThrows(CreationException::class.java) {
      Guice.createInjector(
          DataSourceModule.create("NOT_EXEMPLAR", Exemplar::class),
          ConfigModule.create("my-app", rootConfig)
      )
    }
    val errorMessage = exception.errorMessages.first()
    assertThat(errorMessage.cause).isInstanceOf(IllegalStateException::class.java)
    assertThat(errorMessage.message).contains("no datasource cluster named NOT_EXEMPLAR")
  }

  class PeopleDatabase(private val dataSourceCluster: DataSourceCluster) {
    fun listPeople(): List<Pair<Int, String>> {
      return dataSourceCluster.reader.connection.use { conn ->
        conn.createStatement().use { stmt ->
          stmt.executeQuery("SELECT id, name FROM people ORDER BY id ASC").use { rs ->
            val results = mutableListOf<Pair<Int, String>>()
            while (rs.next()) {
              val id = rs.getInt(1)
              val name = rs.getString(2)
              results.add(id to name)
            }
            results.toList()
          }
        }
      }
    }

    companion object {
      val CREATE_TABLE_PEOPLE = """
        |CREATE TABLE people(
        |  id bigint(20) NOT NULL AUTO_INCREMENT,
        |  name varchar(255) NOT NULL
        |)
        |""".trimMargin()

      val DROP_TABLE_PEOPLE = """
        |DROP TABLE IF EXISTS people
        |""".trimMargin()
    }
  }

  @Qualifier
  annotation class Exemplar

  data class RootConfig(val data_source_clusters: DataSourceClustersConfig) : Config
}
