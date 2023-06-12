package misk

import com.google.common.util.concurrent.AbstractIdleService
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This is a symbolic service that's useful to define the relationship, generally, between
 * services which process traffic (Jetty, SQS, Kinesis, Cron, Tasks, etc.) and services which
 * are required to do work (Database, Redis, GCP, Feature Flags).
 *
 * By having the former depend on ReadyService and the latter enhance ReadyService we can force,
 * for example, JettyService to stop _before_ our feature flag service without having to intertwine
 * our dependency graph.
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
