package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import misk.healthchecks.HealthCheck
import misk.logging.getLogger
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.ConcurrencyLimitsOptOut
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private val logger = getLogger<ReadinessCheckAction>()

@Singleton
class ReadinessCheckAction @Inject internal constructor(
  private val serviceManagerProvider: Provider<ServiceManager>,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>
) : WebAction {

  @Get("/_readiness")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @AvailableWhenDegraded
  fun readinessCheck(): Response<String> {
    val servicesNotRunning = serviceManagerProvider.get().servicesByState().values().asList()
        .filterNot { it.isRunning }

    for (service in servicesNotRunning) {
      logger.info("Service not running: $service")
    }

    if (!servicesNotRunning.isEmpty()) {
      // Don't do healthchecks if services haven't all started. The app isn't in a good state yet,
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
}
