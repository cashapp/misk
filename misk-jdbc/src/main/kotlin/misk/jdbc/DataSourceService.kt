package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provider
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import com.zaxxer.hikari.util.DriverDataSource
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Singleton
import wisp.deployment.Deployment
import wisp.logging.getLogger
import java.time.Duration
import java.util.Properties
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Builds a connection pool to a JDBC database. Doesn't do any schema migration or validation.
 *
 * @param baseConfig the configuration to connect to. The actual database name used may vary as
 *     the [databasePool] can pick an alternate database name for testing.
 */
@Singleton
class DataSourceService @JvmOverloads constructor(
  private val qualifier: KClass<out Annotation>,
  private val baseConfig: DataSourceConfig,
  private val deployment: Deployment,
  private val dataSourceDecorators: Set<DataSourceDecorator>,
  private val databasePool: DatabasePool,
  private val collectorRegistry: CollectorRegistry? = null,
) : AbstractIdleService(), DataSourceConnector, Provider<DataSource> {
  private lateinit var config: DataSourceConfig

  /** The backing connection pool */
  private var hikariDataSource: HikariDataSource? = null

  /** The decorated data source */
  private var _dataSource: DataSource? = null

  val dataSource: DataSource
    get() = _dataSource
      ?: error("@${qualifier.simpleName} DataSource not created: did you forget to start the service?")

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} connection pool")

    require(_dataSource == null)
    try {
      createDataSource(baseConfig)
    } catch (e: Exception) {
      if (!baseConfig.canRecoverOnReplica()) {
        logger.error(e) { "Failed to start the data source." }
        throw e
      }
      logger.error(e) { "Failed to start the data source, trying to do it with replica." }
      createDataSource(baseConfig.asReplica())
    }
    logger.info("Started @${qualifier.simpleName} connection pool in $stopwatch")
  }

  private fun createDataSource(baseConfig: DataSourceConfig) {
    // Rewrite the caller's config to get a database name like "movies__20190730__5" in tests.
    config = databasePool.takeDatabase(baseConfig)

    val hikariConfig = HikariConfig()
    hikariConfig.driverClassName = config.type.driverClassName
    hikariConfig.jdbcUrl = config.buildJdbcUrl(deployment)
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
    hikariConfig.idleTimeout = config.connection_idle_timeout?.toMillis()
      ?: config.connection_max_lifetime.minus(DEFAULT_CONNECTION_IDLE_TIMEOUT_OFFSET).toMillis()
    hikariConfig.maxLifetime = config.connection_max_lifetime.toMillis()
    hikariConfig.keepaliveTime = config.keepalive_time.toMillis()

    if (config.type != DataSourceType.VITESS_MYSQL) {
      // Our Hibernate settings expect autocommit to be disabled, see
      // CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT in SessionFactoryService
      hikariConfig.isAutoCommit = false
    }

    if (config.type == DataSourceType.MYSQL || config.type == DataSourceType.VITESS_MYSQL || config.type == DataSourceType.TIDB) {
      if (!config.use_fixed_pool_size) {
        hikariConfig.minimumIdle = 5
      }

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

    // TODO(sahilm): The same mitigation _might_ be applicable to the DataSourceTypes VITESS_MYSQL and TIDB
    if (config.type == DataSourceType.MYSQL && config.mysql_enforce_writable_connections) {
      /*
          Q. Why isn't this a DataSourceDecorator?
          A. Because HikariCP calls Connection.isValid() in its internal getConnection() method before returning the connection to the DataSourceDecorator.
             Implementing this logic in a DataSourceDecorator is too late and thus must be wrapped in the DataSource that HikariCP uses internally.
          Q. Why can't we use a connectionTestQuery like `SET [SESSION] TRANSACTION READ WRITE` as [proposed](https://groups.google.com/g/hikari-cp/c/VH7nqwGimCs) in the HikariCP mailing list?
          A. Because MySQL does not raise an error if `SET [SESSION] TRANSACTION READ WRITE` is executed on a read only connection.

          TODO(sahilm): Extract ConnectionDecoratingDataSource and WritableConnectionValidator to Wisp so the same mitigation can be used in Armeria,
           should wait for the solution to prove itself first.
       */
      val mysqlDataSource = buildDataSource(hikariConfig)
      hikariConfig.dataSource = ConnectionDecoratingDataSource(
        connectionDecorator = { connection ->
          WritableConnectionValidator(connection)
        },
        dataSource = mysqlDataSource
      )
    }

    collectorRegistry?.let {
      hikariConfig.metricsTrackerFactory = PrometheusMetricsTrackerFactory(it)
    }

    hikariDataSource = HikariDataSource(hikariConfig)
    _dataSource = decorate(hikariDataSource!!)
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

  companion object {
    val logger = getLogger<DataSourceService>()
    private val DEFAULT_CONNECTION_IDLE_TIMEOUT_OFFSET = Duration.ofSeconds(10)
  }

  override fun get(): DataSource {
    return dataSource
  }

  /**
   * Lifted from private method com.zaxxer.hikari.pool.PoolBase#initializeDataSource()
   * @see com.zaxxer.hikari.pool.PoolBase#initializeDataSource()
   */
  private fun buildDataSource(config: HikariConfig): DriverDataSource {
    val jdbcUrl: String = config.jdbcUrl
    val username: String = config.username
    val password: String = config.password
    val driverClassName: String = config.driverClassName
    val dataSourceProperties: Properties = config.dataSourceProperties

    return DriverDataSource(jdbcUrl, driverClassName, dataSourceProperties, username, password)
  }
}
