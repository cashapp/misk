package misk.jdbc

import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.name.Names
import misk.config.Config
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.keyOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.SQLInvalidAuthorizationSpecException
import javax.inject.Qualifier
import javax.sql.DataSource

internal class DataSourceModuleTest {
  val defaultEnv = Environment.TESTING
  val username = "snork"
  val password = "florp"
  val dbname = "lorfil"
  val rootConfig = MiskConfig.load<RootConfig>("test_data_source_app", defaultEnv)

  lateinit var baseDataSource: DataSource

  @BeforeEach
  fun initDatabase() {
    // Create the datasource by hand
    Class.forName(DataSourceType.HSQLDB.driverClassName)

    val datasource = org.hsqldb.jdbc.JDBCDataSource()
    datasource.setURL("jdbc:hsqldb:mem:$dbname")
    datasource.setUser(username)
    datasource.setPassword(password)
    baseDataSource = datasource

    // Add some people that can be retrieved in tests
    val db = PeopleDatabase(baseDataSource)
    db.init()
    db.addPerson(100, "Mary")
    db.addPerson(101, "Phil")
  }

  @AfterEach
  fun destroyDatabase() {
    baseDataSource.connection.use { conn ->
      conn.createStatement().use { stmt ->
            stmt.execute("DROP TABLE people")
          }
      conn.commit()
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
    constructor(dataSource: DataSource) : this(DataSourceCluster(dataSource, dataSource))

    fun init() {
      dataSourceCluster.writer.connection.use { conn ->
        conn.createStatement().use { stmt ->
              stmt.execute("CREATE TABLE people(id INT NOT NULL, name VARCHAR(256) NOT NULL)")
            }
      }
    }

    fun addPerson(id: Int, name: String) {
      dataSourceCluster.writer.connection.use { conn ->
        conn.prepareStatement("INSERT INTO people(id, name) VALUES(?, ?)").use { stmt ->
              stmt.setInt(1, id)
              stmt.setString(2, name)
              stmt.execute()
            }
        conn.commit()
      }
    }

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
  }

  @Qualifier
  annotation class Exemplar

  data class RootConfig(val data_source_clusters: DataSourceClustersConfig) : Config
}
