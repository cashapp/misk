package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import io.prometheus.client.CollectorRegistry
import misk.environment.Environment
import misk.logging.getLogger
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Builds a connection pool to a JDBC database. Doesn't do any schema migration or validation.
 *
 * @param baseConfig the configuration to connect to. The actual database name used may vary as
 *     the [databasePool] can pick an alternate database name for testing.
 */
@Singleton
class DataSourceService(
  private val qualifier: KClass<out Annotation>,
  private val baseConfig: DataSourceConfig,
  private val environment: Environment,
  private val dataSourceDecorators: Set<DataSourceDecorator>,
  private val databasePool: DatabasePool,
  private val collectorRegistry: CollectorRegistry? = null
) : AbstractIdleService(), DataSourceConnector, Provider<DataSource> {
  private lateinit var config: DataSourceConfig
  /** The backing connection pool */
  private var hikariDataSource: HikariDataSource? = null
  /** The decorated data source */
  private var dataSource: DataSource? = null

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} connection pool")

    require(dataSource == null)
    try {
      createDataSource(baseConfig)
    } catch (e: Exception) {
      logger.warn("Fail to start the data source to master tablet, trying to do it with replica")
      createDataSource(baseConfig.asReplica())
    }
    logger.info("Started @${qualifier.simpleName} connection pool in $stopwatch")
  }

  private fun createDataSource(baseConfig: DataSourceConfig) {
    // Rewrite the caller's config to get a database name like "movies__20190730__5" in tests.
    config = databasePool.takeDatabase(baseConfig)

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
    hikariConfig.validationTimeout = config.validation_timeout.toMillis()
    hikariConfig.maxLifetime = config.connection_max_lifetime.toMillis()

    if (config.type != DataSourceType.VITESS && config.type != DataSourceType.VITESS_MYSQL) {
      // Our Hibernate settings expect autocommit to be disabled, see
      // CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT in SessionFactoryService
      hikariConfig.isAutoCommit = false
    }

    if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.VITESS || config.type == DataSourceType.VITESS_MYSQL || config.type == DataSourceType.TIDB) {
      hikariConfig.minimumIdle = 5
      if (config.type == DataSourceType.MYSQL) {
        hikariConfig.connectionInitSql = "SET time_zone = '+00:00'"
      }

      // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
      hikariConfig.dataSourceProperties["cachePrepStmts"] = "true"
      hikariConfig.dataSourceProperties["prepStmtCacheSize"] = "250"
      hikariConfig.dataSourceProperties["prepStmtCacheSqlLimit"] = "2048"
      if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.TIDB) {
        // TODO(jontirsen): Try turning on server side prepared statements again when this issue
        //  has been fixed: https://github.com/vitessio/vitess/issues/5075
        hikariConfig.dataSourceProperties["useServerPrepStmts"] = "true"
      }
      hikariConfig.dataSourceProperties["useLocalSessionState"] = "true"
      hikariConfig.dataSourceProperties["rewriteBatchedStatements"] = "true"
      hikariConfig.dataSourceProperties["cacheResultSetMetadata"] = "true"
      hikariConfig.dataSourceProperties["cacheServerConfiguration"] = "true"
      hikariConfig.dataSourceProperties["elideSetAutoCommits"] = "true"
      hikariConfig.dataSourceProperties["maintainTimeStats"] = "false"
      hikariConfig.dataSourceProperties["characterEncoding"] = "UTF-8"
    }

    collectorRegistry?.let {
      hikariConfig.metricsTrackerFactory = PrometheusMetricsTrackerFactory(it)
    }

    hikariDataSource = HikariDataSource(hikariConfig)
    dataSource = decorate(hikariDataSource!!)
  }

  private fun decorate(dataSource: DataSource): DataSource =
      dataSourceDecorators.fold(dataSource) { ds, decorator -> decorator.decorate(ds) }

  override fun config(): DataSourceConfig = this.config

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping @${qualifier.simpleName} connection pool")

    require(hikariDataSource != null)
    hikariDataSource!!.close()
    databasePool.releaseDatabase(config)

    logger.info("Stopped @${qualifier.simpleName} connection pool in $stopwatch")
  }

  override fun get(): DataSource {
    return dataSource ?: throw IllegalStateException(
        "@${qualifier.simpleName} DataSource not created: did you forget to start the service?")
  }

  companion object {
    val logger = getLogger<DataSourceService>()
  }
}
