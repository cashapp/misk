package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import misk.healthchecks.HealthCheck
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebConfig
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.*
import misk.healthchecks.HealthStatus
import java.time.Clock
import java.time.Instant

private val logger = getLogger<ReadinessCheckAction>()

@Singleton
class ReadinessCheckAction @Inject internal constructor(
  private val serviceManagerProvider: Provider<ServiceManager>,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>,
  val config: WebConfig,
  val clock: Clock
) : WebAction {

  var lastUpdate: Instant? = null
  var statuses: List<HealthStatus> = listOf()

  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @AvailableWhenDegraded
  fun readinessCheck(): Response<String> = runBlocking {
    val servicesNotRunning = serviceManagerProvider.get().servicesByState().values().asList()
      .filterNot { it.isRunning }

    for (service in servicesNotRunning) {
      logger.info("Service not running: $service")
    }

    if (!servicesNotRunning.isEmpty()) {
      // Don't do healthchecks if services haven't all started. The app isn't in a good state yet,
      // and a health check could end up triggering random errors that we don't want to flood the
      // logs with.
      return@runBlocking Response("", statusCode = 503)
    }

    // First run needs to block and get statuses
    if (lastUpdate == null) {
      refreshStatuses()
    } else {
      // This needs to be independent of the max age check so that services can recover
      // If refreshStatuses() is never run then a failed/stale check can never recover
      if (clock.instant().isAfter(lastUpdate!!.plusMillis(config.readiness_refresh_interval_ms.toLong()))) {
        // Launch in the background so that we can still return the cached state
        launch(Job()) {
          refreshStatuses()
        }
      }

      if (clock.instant().isAfter(lastUpdate!!.plusMillis(config.readiness_max_age_ms.toLong()))) {
        logger.info("Failed health check: last status check is older than max age")
        return@runBlocking Response("", statusCode = 503)
      }
    }

    val failedHealthChecks = statuses.filter { !it.isHealthy }

    if (failedHealthChecks.isEmpty()) {
      return@runBlocking Response("", statusCode = 200)
    }

    for (healthCheck in failedHealthChecks) {
      logger.info("Failed health check: ${healthCheck.messages}")
    }
    return@runBlocking Response("", statusCode = 503)
  }

  private fun refreshStatuses() {
    statuses = healthChecks.map { it.status() }
    lastUpdate = clock.instant()
  }
}
