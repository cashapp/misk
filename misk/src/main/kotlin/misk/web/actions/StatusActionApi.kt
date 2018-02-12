package misk.web.actions

import misk.healthchecks.HealthCheck
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusActionApi : WebAction {
  @Inject lateinit var healthChecks: MutableSet<HealthCheck>

  @Get("/api/_status")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun statusActionApi(): Status {
    return Status(
        healthChecks.toList().map { it ->
          HealthStatus(
              it.javaClass.name, it.status().isHealthy, it.status().messages
          )
        })
  }

  companion object {
    data class Status(val healthChecks: List<HealthStatus>)

    data class HealthStatus(
        val name: String,
        val isHealthy: Boolean,
        val messages: List<String>
    )
  }
}
