package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import misk.environment.Environment
import misk.logging.getLogger
import javax.inject.Provider
import kotlin.reflect.KClass

private val logger = getLogger<HibernateService>()

internal class HibernateService(
  private val environment: Environment,
  private val qualifier: KClass<out Annotation>,
  private val connector: HibernateConnector,
  private val schemaMigratorProvider: Provider<SchemaMigrator> // Lazy!
) : AbstractIdleService() {
  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} Hibernate")

    connector.connect()

    val schemaMigrator = schemaMigratorProvider.get()
    if (environment == Environment.TESTING || environment == Environment.DEVELOPMENT) {
      val appliedVersions = schemaMigrator.initialize()
      schemaMigrator.applyAll("HibernateService", appliedVersions)
    } else {
      schemaMigrator.requireAll()
    }

    logger.info("Started @${qualifier.simpleName} Hibernate in $stopwatch")
  }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping @${qualifier.simpleName} Hibernate")
    connector.disconnect()
    logger.info("Shopped @${qualifier.simpleName} Hibernate in $stopwatch")
  }
}
