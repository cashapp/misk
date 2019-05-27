package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import misk.environment.Environment
import misk.metrics.Metrics
import mu.KotlinLogging
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Builds a connection pool to a JDBC database. Doesn't do any schema migration or validation.
 */
@Singleton
internal class DataSourceService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val environment: Environment,
  private val dataSourceDecorators: Set<DataSourceDecorator>,
  private val metrics: Metrics? = null
) : AbstractIdleService(), Provider<DataSource> {
  /** The backing connection pool */
  private var hikariDataSource: HikariDataSource? = null
  /** The decorated data source */
  private var dataSource: DataSource? = null

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} connection pool")

    require(dataSource == null)

    val config = HikariConfig()
    config.driverClassName = this.config.type.driverClassName
    config.jdbcUrl = this.config.buildJdbcUrl(environment)
    if (this.config.username != null) {
      config.username = this.config.username
    }
    if (this.config.password != null) {
      config.password = this.config.password
    }
    config.minimumIdle = this.config.fixed_pool_size
    config.maximumPoolSize = this.config.fixed_pool_size
    config.poolName = qualifier.simpleName

    if (this.config.type == DataSourceType.MYSQL || this.config.type == DataSourceType.VITESS) {
      config.minimumIdle = 5
      if (this.config.type == DataSourceType.MYSQL) {
        config.connectionInitSql = "SET time_zone = '+00:00'"
      }

      // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
      config.dataSourceProperties["cachePrepStmts"] = "true"
      config.dataSourceProperties["prepStmtCacheSize"] = "250"
      config.dataSourceProperties["prepStmtCacheSqlLimit"] = "2048"
      config.dataSourceProperties["useServerPrepStmts"] = "true"
      config.dataSourceProperties["useLocalSessionState"] = "true"
      config.dataSourceProperties["rewriteBatchedStatements"] = "true"
      config.dataSourceProperties["cacheResultSetMetadata"] = "true"
      config.dataSourceProperties["cacheServerConfiguration"] = "true"
      config.dataSourceProperties["elideSetAutoCommits"] = "true"
      config.dataSourceProperties["maintainTimeStats"] = "false"
    }

    metrics?.let { config.metricsTrackerFactory = PrometheusMetricsTrackerFactory(it.registry) }

    hikariDataSource = HikariDataSource(config)
    dataSource = decorate(hikariDataSource!!)

    logger.info("Started @${qualifier.simpleName} connection pool in $stopwatch")
  }

  private fun decorate(dataSource: DataSource): DataSource =
      dataSourceDecorators.fold(dataSource) { ds, decorator -> decorator.decorate(ds) }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping @${qualifier.simpleName} connection pool")

    require(hikariDataSource != null)
    hikariDataSource!!.close()

    logger.info("Stopped @${qualifier.simpleName} connection pool in $stopwatch")
  }

  override fun get(): DataSource {
    return dataSource ?: throw IllegalStateException(
        "@${qualifier.simpleName} DataSouce not created: did you forget to start the service?")
  }

  companion object {
    val logger = KotlinLogging.logger {}
  }
}
