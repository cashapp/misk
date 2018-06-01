package misk.web.actions

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.State
import misk.logging.getLogger
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<LivenessCheckAction>()
private val failedServiceStates = listOf(State.FAILED, State.TERMINATED)

@Singleton
class LivenessCheckAction @Inject internal constructor(
  private val services: List<Service>
) : WebAction {

  @Get("/_liveness")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun livenessCheck(): Response<String> {
    val failedServices = services.filter {
      failedServiceStates.contains(it.state())
    }

    if (failedServices.isEmpty()) {
      return Response("", statusCode = 200)
    }

    for (service in failedServices) {
      logger.info("Service failed: $service")
    }
    return Response("", statusCode = 503)
  }
}
