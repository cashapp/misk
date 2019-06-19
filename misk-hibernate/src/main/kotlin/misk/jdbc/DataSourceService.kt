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

    val hikariConfig = HikariConfig()
    hikariConfig.driverClassName = config.type.driverClassName
    hikariConfig.jdbcUrl = config.buildJdbcUrl(environment)
    if (config.username != null) {
      hikariConfig.username = config.username
    }
    if (config.password != null) {
      hikariConfig.password = config.password
    }
    hikariConfig.minimumIdle = config.fixed_pool_size
    hikariConfig.maximumPoolSize = config.fixed_pool_size
    hikariConfig.poolName = qualifier.simpleName
    hikariConfig.connectionTimeout = config.connection_timeout.toMillis()
    hikariConfig.maxLifetime = config.connection_max_lifetime.toMillis()

    if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.VITESS) {
      hikariConfig.minimumIdle = 5
      if (config.type == DataSourceType.MYSQL) {
        hikariConfig.connectionInitSql = "SET time_zone = '+00:00'"
      }

      // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
      hikariConfig.dataSourceProperties["cachePrepStmts"] = "true"
      hikariConfig.dataSourceProperties["prepStmtCacheSize"] = "250"
      hikariConfig.dataSourceProperties["prepStmtCacheSqlLimit"] = "2048"
      hikariConfig.dataSourceProperties["useServerPrepStmts"] = "true"
      hikariConfig.dataSourceProperties["useLocalSessionState"] = "true"
      hikariConfig.dataSourceProperties["rewriteBatchedStatements"] = "true"
      hikariConfig.dataSourceProperties["cacheResultSetMetadata"] = "true"
      hikariConfig.dataSourceProperties["cacheServerConfiguration"] = "true"
      hikariConfig.dataSourceProperties["elideSetAutoCommits"] = "true"
      hikariConfig.dataSourceProperties["maintainTimeStats"] = "false"
    }

    metrics?.let { hikariConfig.metricsTrackerFactory = PrometheusMetricsTrackerFactory(it.registry) }

    hikariDataSource = HikariDataSource(hikariConfig)
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
