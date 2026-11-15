package misk

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.logging.getLogger

/**
 * This is a symbolic service that's useful to define the relationship, generally, between services which process
 * traffic (Jetty, SQS, Kinesis, Cron, Tasks, etc.) and services which are required to do work (Database, Redis, GCP,
 * Feature Flags).
 *
 * By having the former depend on ReadyService and the latter enhanced by ReadyService we can force, for example,
 * JettyService to stop _before_ our feature flag service without having to intertwine our dependency graph.
 *
 * Example
 *
 * ```kotlin
 * install(
 *   ServiceModule<TransacterService>(qualifier)
 *     .enhancedBy<SchemaMigratorService>(qualifier)
 *     // ReadyService won't run until TransacterService is complete
 *     .enhancedBy<ReadyService>()
 *     .dependsOn<DataSourceService>(qualifier)
 * )
 * ```
 */
@Singleton
class ReadyService @Inject constructor() : AbstractIdleService() {
  override fun startUp() {
    logger.info { "Starting ready service" }
  }

  override fun shutDown() {
    logger.info { "Stopping ready service" }
  }

  companion object {
    private val logger = getLogger<ReadyService>()
  }
}
