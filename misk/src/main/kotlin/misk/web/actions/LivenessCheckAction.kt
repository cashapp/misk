package misk.web.actions

import com.google.common.util.concurrent.Service.State
import com.google.common.util.concurrent.ServiceManager
import misk.logging.getLogger
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private val logger = getLogger<LivenessCheckAction>()

@Singleton
class LivenessCheckAction @Inject internal constructor(
  private val serviceManagerProvider: Provider<ServiceManager>
) : WebAction {

  @Get("/_liveness")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @Unauthenticated
  @AvailableWhenDegraded
  fun livenessCheck(): Response<String> {
    val serviceManager = serviceManagerProvider.get()
    val failedServices = serviceManager.servicesByState().get(State.FAILED) +
        serviceManager.servicesByState().get(State.TERMINATED)

    if (failedServices.isEmpty()) {
      return Response("", statusCode = 200)
    }

    for (service in failedServices) {
      logger.info("Service failed: $service")
    }
    return Response("", statusCode = 503)
  }
}
