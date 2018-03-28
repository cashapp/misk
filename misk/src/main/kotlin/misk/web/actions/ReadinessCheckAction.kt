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
class ReadinessCheckAction @Inject internal constructor(
  private val services: List<Service>,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>
) : WebAction {

  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun readinessCheck(): Response<String> {
    val isReady = services.all { it.isRunning }
        && healthChecks.all { it.status().isHealthy }

    // TODO(jgulbronson) - Should return an empty body
    return Response(
        "",
        statusCode = if (isReady) 200 else 503
    )
  }
}
