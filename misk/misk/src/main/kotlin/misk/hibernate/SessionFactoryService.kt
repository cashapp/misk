package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import com.zaxxer.hikari.hibernate.HikariConnectionProvider
import misk.DependentService
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.getLogger
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.integrator.spi.Integrator
import org.hibernate.service.spi.SessionFactoryServiceRegistry
import javax.inject.Provider
import kotlin.reflect.KClass

private val logger = getLogger<SessionFactoryService>()

/**
 * Builds a bare connection to a Hibernate database. Doesn't do any schema migration or validation.
 */
internal class SessionFactoryService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val entityClasses: Set<HibernateEntity> = setOf(),
  private val eventListeners: Set<HibernateEventListener> = setOf()
) : AbstractIdleService(), DependentService, Provider<SessionFactory> {
  private var sessionFactory: SessionFactory? = null

  override val consumedKeys = setOf<Key<*>>()
  override val producedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(qualifier))

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} Hibernate")

    require(sessionFactory == null)

    // Register event listeners.
    val integrator = object : Integrator {
      override fun integrate(
        metadata: Metadata,
        sessionFactory: SessionFactoryImplementor,
        serviceRegistry: SessionFactoryServiceRegistry
      ) {
        val eventListenerRegistry = serviceRegistry.getService(EventListenerRegistry::class.java)
        for (eventListener in eventListeners) {
          eventListener.register(eventListenerRegistry)
        }
      }

      override fun disintegrate(
        sessionFactory: SessionFactoryImplementor,
        serviceRegistry: SessionFactoryServiceRegistry
      ) {
      }
    }

    val bootstrapRegistryBuilder = BootstrapServiceRegistryBuilder()
        .applyIntegrator(integrator)
        .build()

    val registryBuilder = StandardServiceRegistryBuilder(bootstrapRegistryBuilder)
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
    val metadataBuilder = metadataSources.metadataBuilder
    metadataBuilder.applyBasicType(IdType, Id::class.qualifiedName)
    val metadata = metadataBuilder.build()
    sessionFactory = metadata.buildSessionFactory()

    logger.info("Started @${qualifier.simpleName} Hibernate in $stopwatch")
  }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping @${qualifier.simpleName} Hibernate")

    require(sessionFactory != null)
    sessionFactory!!.close()

    logger.info("Stopped @${qualifier.simpleName} Hibernate in $stopwatch")
  }

  override fun get(): SessionFactory {
    return sessionFactory ?: throw IllegalStateException(
        "@$qualifier Hibernate not connected: did you forget to start the service?")
  }
}