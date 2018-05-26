package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.zaxxer.hikari.hibernate.HikariConnectionProvider
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.getLogger
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import kotlin.reflect.KClass

private val logger = getLogger<HibernateService>()

internal class HibernateService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val entityClasses: Set<HibernateEntity>
) : AbstractIdleService() {
  lateinit var sessionFactory: SessionFactory

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} Hibernate")

    val registryBuilder = StandardServiceRegistryBuilder()
    registryBuilder.run {
      applySetting(AvailableSettings.DRIVER, config.type.driverClassName)
      applySetting(AvailableSettings.URL, config.type.buildJdbcUrl(config))
      applySetting(AvailableSettings.USER, config.username)
      applySetting(AvailableSettings.PASS, config.password)
      applySetting(AvailableSettings.POOL_SIZE, config.fixed_pool_size.toString())
      applySetting(AvailableSettings.DIALECT, config.type.hibernateDialect)
      applySetting(AvailableSettings.SHOW_SQL, "false")
      applySetting(AvailableSettings.USE_SQL_COMMENTS, "true")
      applySetting(AvailableSettings.CONNECTION_PROVIDER, HikariConnectionProvider::class.java.name)
      applySetting(AvailableSettings.USE_GET_GENERATED_KEYS, "true")
      applySetting("hibernate.hikari.poolName", qualifier.simpleName)

      if (config.type == DataSourceType.MYSQL) {
        applySetting("hibernate.hikari.minimumIdle", "5")
        applySetting("hibernate.hikari.driverClassName", "com.mysql.jdbc.Driver")
        applySetting("hibernate.hikari.connectionInitSql", "SET time_zone = '+00:00'")

        // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
        applySetting("hibernate.hikari.dataSource.cachePrepStmts", "true")
        applySetting("hibernate.hikari.dataSource.prepStmtCacheSize", "250")
        applySetting("hibernate.hikari.dataSource.prepStmtCacheSqlLimit", "2048")
        applySetting("hibernate.hikari.dataSource.useServerPrepStmts", "true")
        applySetting("hibernate.hikari.dataSource.useLocalSessionState", "true")
        applySetting("hibernate.hikari.dataSource.rewriteBatchedStatements", "true")
        applySetting("hibernate.hikari.dataSource.cacheResultSetMetadata", "true")
        applySetting("hibernate.hikari.dataSource.cacheServerConfiguration", "true")
        applySetting("hibernate.hikari.dataSource.elideSetAutoCommits", "true")
        applySetting("hibernate.hikari.dataSource.maintainTimeStats", "false")
      }
    }

    val registry = registryBuilder.build()

    val metadataSources = MetadataSources(registry)
    for (entityClass in entityClasses) {
      metadataSources.addAnnotatedClass(entityClass.entity.java)
    }
    val metadata = metadataSources.buildMetadata()

    sessionFactory = metadata.buildSessionFactory()

    logger.info("Started @${qualifier.simpleName} Hibernate in $stopwatch")
  }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping @${qualifier.simpleName} Hibernate")

    sessionFactory.close()

    logger.info("Shopped @${qualifier.simpleName} Hibernate in $stopwatch")
  }
}
