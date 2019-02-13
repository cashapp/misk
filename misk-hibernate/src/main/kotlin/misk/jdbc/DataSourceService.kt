package misk.jdbc

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import misk.DependentService
import misk.environment.Environment
import misk.inject.toKey
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
  private val dataSourceDecorators: Set<DataSourceDecorator>
) : AbstractIdleService(), DependentService, Provider<DataSource> {
  /** The backing connection pool */
  private var hikariDataSource: HikariDataSource? = null
  /** The decorated data source */
  private var dataSource: DataSource? = null

  override val consumedKeys = setOf<Key<*>>(PingDatabaseService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>(DataSourceService::class.toKey(qualifier))

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

    // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-using-ssl.html
    if (!this.config.trust_certificate_key_store_url.isNullOrBlank()) {
      require(!this.config.trust_certificate_key_store_password.isNullOrBlank()) {
        "must provide a trust_certificate_key_store_password"
      }
      config.dataSourceProperties["trustCertificateKeyStoreUrl"] =
          this.config.trust_certificate_key_store_url
      config.dataSourceProperties["trustCertificateKeyStorePassword"] =
          this.config.trust_certificate_key_store_password
      config.dataSourceProperties["verifyServerCertificate"] = "true"
      config.dataSourceProperties["useSSL"] = "true"
      config.dataSourceProperties["requireSSL"] = "true"
    }

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
