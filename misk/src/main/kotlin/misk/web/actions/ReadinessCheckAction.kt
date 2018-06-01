package misk.web.actions

import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.logging.getLogger
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<ReadinessCheckAction>()

@Singleton
class ReadinessCheckAction @Inject internal constructor(
  private val services: List<Service>,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>
) : WebAction {

  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun readinessCheck(): Response<String> {
    val servicesNotRunning = services.filter { !it.isRunning }
    val failedHealthChecks = healthChecks
        .map { it.status() }
        .filter { !it.isHealthy }

    if (servicesNotRunning.isEmpty() && failedHealthChecks.isEmpty()) {
      return Response("", statusCode = 200)
    }

    for (service in servicesNotRunning) {
      logger.info("Service not running: $service")
    }
    for (healthCheck in failedHealthChecks) {
      logger.info("Failed health check: ${healthCheck.messages}")
    }
    return Response("", statusCode = 503)
  }
}
