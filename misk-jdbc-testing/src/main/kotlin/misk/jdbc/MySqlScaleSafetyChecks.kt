package misk.jdbc

import com.zaxxer.hikari.util.DriverDataSource
import net.ttddyy.dsproxy.proxy.ProxyConfig
import net.ttddyy.dsproxy.support.ProxyDataSource
import wisp.deployment.TESTING
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.util.Properties
import javax.inject.Singleton
import javax.sql.DataSource

@Deprecated("Replace the dependency on misk-jdcb-testing with testFixtures(misk-jdbc)")
@Singleton
class MySqlScaleSafetyChecks(
  val config: DataSourceConfig,
) : DataSourceDecorator {
  private val connection: Connection by lazy { connect() }
  private val fullTableScanDetector = TableScanDetector()

  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.MYSQL) return dataSource

    val proxy = ProxyDataSource(dataSource)
    ScaleSafetyChecks.turnOnSqlGeneralLogging(connection)

    proxy.proxyConfig = ProxyConfig.Builder()
      .methodListener(fullTableScanDetector)
      .build()
    proxy.addListener(fullTableScanDetector)
    return proxy
  }

  fun connect(): Connection {
    return try {
      DriverDataSource(
        config.buildJdbcUrl(TESTING),
        config.type.driverClassName,
        Properties(),
        config.username,
        config.password
      ).connection
    } catch (e: SQLException) {
      throw IllegalStateException("Could not connect to test MySQL server!", e)
    }
  }

  inner class TableScanDetector : ExtendedQueryExecutionListener() {
    private val mysqlTimeBeforeQuery: ThreadLocal<Timestamp?> =
      ThreadLocal.withInitial { null }

    override fun beforeQuery(query: String) {
      if (!CheckDisabler.isCheckEnabled(Check.TABLE_SCAN)) return
      mysqlTimeBeforeQuery.set(ScaleSafetyChecks.getLastLoggedCommand(connection))
    }

    override fun afterQuery(query: String) {
      if (!CheckDisabler.isCheckEnabled(Check.TABLE_SCAN)) return
      val mysqlTime = mysqlTimeBeforeQuery.get() ?: return
      val queries = ScaleSafetyChecks.extractQueriesSince(connection, mysqlTime)

      for (rawQuery in queries) {
        ScaleSafetyChecks.checkQueryForTableScan(connection, null, rawQuery)
      }
    }
  }
}
