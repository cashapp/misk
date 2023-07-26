package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebConfig
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<ReadinessCheckAction>()

@Singleton
class ReadinessCheckAction @Inject internal constructor(
  private val readinessService: ReadinessCheckService,
  private val config: WebConfig,
  private val clock: Clock,
) : WebAction {

  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @AvailableWhenDegraded
  fun readinessCheck(): Response<String> {

    val (lastUpdate, statuses) = readinessService.status
      // Null until first run has finished, which means not yet ready
      ?: return Response("", statusCode = 503)

    if (clock.instant().isAfter(lastUpdate.plusMillis(config.readiness_max_age_ms.toLong()))) {
      logger.info("Failed health check: last status check is older than max age")
      return Response("", statusCode = 503)
    }

    val failedHealthChecks = statuses.filter { !it.isHealthy }

    if (failedHealthChecks.isEmpty()) {
      return Response("", statusCode = 200)
    }

    for (healthCheck in failedHealthChecks) {
      logger.info("Failed health check: ${healthCheck.messages}")
    }
    return Response("", statusCode = 503)
  }
}

