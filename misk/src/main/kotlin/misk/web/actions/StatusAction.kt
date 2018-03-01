package misk.web.actions

import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the current status of the service for programmatic tools that want to
 * query for the current state of the server
 */
@Singleton
class StatusAction @Inject internal constructor(
  private val services: List<Service>,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>
) : WebAction {

  @Get("/_status")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun getStatus(): ServerStatus {
    val serviceStatus = services.map { it.javaClass.simpleName to it.state() }.toMap()
    val healthCheckStatus = healthChecks.map { it.javaClass.simpleName to it.status() }.toMap()
    return ServerStatus(serviceStatus, healthCheckStatus)
  }

  data class ServerStatus(
    val serviceStatus: Map<String, Service.State>,
    val healthCheckStatus: Map<String, HealthStatus>
  )

}