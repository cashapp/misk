package misk.jdbc

import com.google.common.util.concurrent.AbstractIdleService
import misk.environment.Environment
import org.hsqldb.jdbc.JDBCDataSource

class InMemoryHsqlService(
  val config: DataSourceConfig,
  val environment: Environment,
  val setUpStatements: List<String> = listOf(),
  val tearDownStatements: List<String> = listOf()
) : AbstractIdleService() {
  lateinit var datasource: JDBCDataSource

  override fun startUp() {
    Class.forName(DataSourceType.HSQLDB.driverClassName)

    datasource = JDBCDataSource()
    datasource.setURL(config.type.buildJdbcUrl(config, environment))
    datasource.setUser(config.username)
    datasource.setPassword(config.password)

    for (s in setUpStatements) {
      datasource.connection.createStatement().use { statement ->
        statement.execute(s)
      }
    }
  }

  override fun shutDown() {
    for (s in tearDownStatements) {
      datasource.connection.createStatement().use { statement ->
        statement.execute(s)
      }
    }

    datasource.connection.close()
  }
}