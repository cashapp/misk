package misk.jdbc

import com.zaxxer.hikari.util.DriverDataSource
import wisp.deployment.TESTING
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.sql.DataSource

/**
 * Implementation of [TestDatabasePool.Backend] for SQL databases.
 */
@Singleton
internal class MySqlTestDatabasePoolBackend @Inject constructor(
  val config: DataSourceConfig
) : TestDatabasePool.Backend {
  internal val connection: Connection by lazy {
    try {
      DriverDataSource(
        config.buildJdbcUrl(TESTING),
        config.type.driverClassName,
        Properties(),
        config.username,
        config.password
      ).connect()
    } catch (e: SQLException) {
      throw IllegalStateException("Could not connect to test MySQL server!", e)
    }
  }

  /** Kotlin think's that getConnection is a val, but it's really a function. */
  @Suppress("UsePropertyAccessSyntax")
  private fun DataSource.connect() = this.getConnection()

  override fun showDatabases(): Set<String> {
    return connection.showDatabases()
  }

  override fun dropDatabase(name: String) {
    return connection.dropDatabase(name)
  }

  override fun createDatabase(name: String) {
    return connection.createDatabase(name)
  }

  private fun Connection.showDatabases(): Set<String> {
    return createStatement().use { statement ->
      statement.executeQuery("SHOW DATABASES")
        .map { resultSet -> resultSet.getString(1) }
        .toSet()
    }
  }

  private fun Connection.createDatabase(name: String) {
    return createStatement().use { statement ->
      statement.execute("CREATE DATABASE $name")
    }
  }

  private fun Connection.dropDatabase(name: String) {
    return createStatement().use { statement ->
      statement.execute("DROP DATABASE $name")
    }
  }
}
