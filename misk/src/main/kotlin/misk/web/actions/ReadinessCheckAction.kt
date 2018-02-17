package misk.web.actions

import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadinessCheckAction : WebAction {
  @Inject lateinit var services: MutableSet<Service>
  @Inject lateinit var healthChecks: MutableSet<HealthCheck>

  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun readinessCheck(): Response<String> {
    val isReady = services.all { it.isRunning }
        && healthChecks.all {
      it.status()
          .isHealthy
    }

    // TODO(jgulbronson) - Should return an empty body
    return Response(
        "",
        statusCode = if (isReady) 200 else 503
    )
  }
}
