package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import misk.jdbc.DataSourceConfig
import misk.logging.getLogger
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import javax.inject.Singleton
import kotlin.reflect.KClass

private val logger = getLogger<HibernateService>()

@Singleton
internal class HibernateService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val entityClasses: Set<KClass<*>>
) : AbstractIdleService() {
  lateinit var sessionFactory: SessionFactory

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} Hibernate")

    val registry = StandardServiceRegistryBuilder()
        .applySetting("hibernate.connection.driver_class", config.type.driverClassName)
        .applySetting("hibernate.connection.url", config.type.buildJdbcUrl(config))
        .applySetting("hibernate.connection.username", config.username)
        .applySetting("hibernate.connection.password", config.password)
        .applySetting("hibernate.connection.pool_size", config.fixed_pool_size.toString())
        .applySetting("hibernate.dialect", config.type.hibernateDialect)
        .applySetting("hibernate.show_sql", "false")
        .build()

    val metadataSources = MetadataSources(registry)
    for (entityClass in entityClasses) {
      metadataSources.addAnnotatedClass(entityClass.java)
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
