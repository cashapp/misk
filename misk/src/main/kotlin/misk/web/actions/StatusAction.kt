package misk.web.actions

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import misk.DelegatingService
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Returns the current status of the service for programmatic tools that want to
 * query for the current state of the server
 */
@Singleton
class StatusAction @Inject internal constructor(
  private val serviceManagerProvider: Provider<ServiceManager>,
  private val clock: Clock,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>
) : WebAction {
  private var lastUnhealthyAt: Instant? = null

  @Get("/_status")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @AvailableWhenDegraded
  fun getStatus(): ServerStatus {
    val services = serviceManagerProvider.get().servicesByState().values().asList()
    val serviceStatus = services.map {
      when (it) {
        is DelegatingService -> it.service.javaClass.simpleName to it.state()
        else -> it.javaClass.simpleName to it.state()
      }
    }.toMap()
    val healthCheckStatus = healthChecks.map { it.javaClass.simpleName to it.status() }.toMap()
    maybeLog(healthCheckStatus)
    return ServerStatus(serviceStatus, healthCheckStatus)
  }

  /** Log a warning if a health check fails, no more than once every 5 seconds. */
  private fun maybeLog(healthCheckStatus: Map<String, HealthStatus>) {
    val now = clock.instant()
    if (healthCheckStatus.values.any { !it.isHealthy }) {
      if (lastUnhealthyAt?.plusSeconds(5)?.isBefore(now) == false) return
      lastUnhealthyAt = now
      logger.warn("health checks failed: $healthCheckStatus")
    }
  }

  data class ServerStatus(
    val serviceStatus: Map<String, Service.State>,
    val healthCheckStatus: Map<String, HealthStatus>
  )

  companion object {
    private val logger = getLogger<StatusAction>()
  }
}
