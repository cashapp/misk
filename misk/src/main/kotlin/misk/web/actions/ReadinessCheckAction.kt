package misk.web.actions

import com.google.common.util.concurrent.Service.State
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import misk.logging.getLogger

@Singleton
class ReadinessCheckAction @Inject internal constructor(
  private val serviceManagerProvider: Provider<ServiceManager>,
  private val healthChecks: List<HealthCheck>,
) : WebAction {
  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @AvailableWhenDegraded
  fun readinessCheck(): Response<String> {
    val servicesNotRunning = serviceManagerProvider.get().servicesByState().values().filterNot {
      it.isRunning
    }

    for (service in servicesNotRunning) {
      // Only log failed services.
      if (service.state() == State.FAILED) {
        logger.info("Service not running: $service")
      }
    }

    if (servicesNotRunning.isNotEmpty()) {
      // Don't do health checks if services haven't all started. The app isn't in a good state yet,
      // and a health check could end up triggering random errors that we don't want to flood the
      // logs with.
      return Response("", statusCode = 503)
    }

    val failedHealthChecks = healthChecks
      .map { it.status() }
      .filter { !it.isHealthy }

    if (failedHealthChecks.isEmpty()) {
      return Response("", statusCode = 200)
    }

    for (healthCheck in failedHealthChecks) {
      logger.info("Failed health check: ${healthCheck.messages}")
    }
    return Response("", statusCode = 503)
  }

  companion object {
    private val logger = getLogger<ReadinessCheckAction>()
  }
}
