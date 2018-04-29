package misk.jdbc

import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.name.Names
import misk.config.Config
import misk.config.ConfigModule
import misk.inject.keyOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Qualifier
import javax.sql.DataSource

internal class DataSourceModuleTest {
  val username = "snork"
  val password = "florp"
  val dbname = "lorfil"
  val rootConfig = RootConfig(
      DataSourcesConfig(mapOf(
          "exemplar" to DataSourceConfig(
              DataSourceType.HSQLDB,
              database = dbname,
              username = username,
              password = password
          ))))

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

    val datasource = injector.getInstance(keyOf<DataSource>())
    val db = PeopleDatabase(datasource)
    val results = db.listPeople()
    assertThat(results).containsExactly(100 to "Mary", 101 to "Phil")
  }

  @Test fun bindsDataSourceWithAnnotationInstance() {
    val injector = Guice.createInjector(
        DataSourceModule.create("exemplar", Names.named("exemplar")),
        ConfigModule.create("my-app", rootConfig))

    val datasource = injector.getInstance(keyOf<DataSource>(Names.named("exemplar")))
    val db = PeopleDatabase(datasource)
    val results = db.listPeople()
    assertThat(results).containsExactly(100 to "Mary", 101 to "Phil")
  }

  @Test fun bindsDataSourceWithAnnotation() {
    val injector = Guice.createInjector(
        DataSourceModule.create("exemplar", ForWrites::class),
        ConfigModule.create("my-app", rootConfig))

    val datasource = injector.getInstance(keyOf(DataSource::class, ForWrites::class))
    val db = PeopleDatabase(datasource)
    val results = db.listPeople()
    assertThat(results).containsExactly(100 to "Mary", 101 to "Phil")
  }

  @Test fun usesProperUsername() {
    assertThrows(CreationException::class.java) {
      Guice.createInjector(
          DataSourceModule.create("exemplar"),
          ConfigModule.create("my-app", RootConfig(
              DataSourcesConfig(mapOf(
                  "exemplar" to DataSourceConfig(
                      DataSourceType.HSQLDB,
                      database = dbname,
                      username = username + "INCORRECT",
                      password = password
                  )))))
      )
    }
  }

  @Test fun usesProperPassword() {
    assertThrows(CreationException::class.java) {
      Guice.createInjector(
          DataSourceModule.create("exemplar"),
          ConfigModule.create("my-app", RootConfig(
              DataSourcesConfig(mapOf(
                  "exemplar" to DataSourceConfig(
                      DataSourceType.HSQLDB,
                      database = dbname,
                      username = username,
                      password = password + "INCORRECT"
                  )))))
      )
    }
  }

  @Test fun failsIfDataSourceNotFound() {
    assertThrows(CreationException::class.java) {
      Guice.createInjector(
          DataSourceModule.create("NOT_EXEMPLAR", ForWrites::class),
          ConfigModule.create("my-app", RootConfig(
              DataSourcesConfig(mapOf(
                  "exemplar" to DataSourceConfig(
                      DataSourceType.HSQLDB,
                      database = dbname,
                      username = username,
                      password = password
                  )))))
      )
    }
  }

  class PeopleDatabase(private val datasource: DataSource) {
    fun init() {
      datasource.connection.use { conn ->
        conn.createStatement().use { stmt ->
              stmt.execute("CREATE TABLE people(id INT NOT NULL, name VARCHAR(256) NOT NULL)")
            }
      }
    }

    fun addPerson(id: Int, name: String) {
      datasource.connection.use { conn ->
        conn.prepareStatement("INSERT INTO people(id, name) VALUES(?, ?)").use { stmt ->
              stmt.setInt(1, id)
              stmt.setString(2, name)
              stmt.execute()
            }
        conn.commit()
      }
    }

    fun listPeople(): List<Pair<Int, String>> {
      return datasource.connection.use { conn ->
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
  annotation class ForWrites

  data class RootConfig(val data_sources: DataSourcesConfig) : Config
}