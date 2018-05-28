package misk.hibernate

import com.zaxxer.hikari.hibernate.HikariConnectionProvider
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Builds a bare connection to a Hibernate database. Doesn't do any schema migration or validation.
 */
internal class HibernateConnector(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val entityClasses: Set<HibernateEntity>
) : Provider<SessionFactory> {
  private var sessionFactory: SessionFactory? = null

  fun connect() {
    require(sessionFactory == null)

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
      applySetting(AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS, "false")
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
  }

  fun disconnect() {
    require(sessionFactory != null)
    sessionFactory!!.close()
  }

  override fun get(): SessionFactory {
    return sessionFactory ?: throw IllegalStateException("@$qualifier Hibernate not connected")
  }
}