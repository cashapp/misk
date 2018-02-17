package misk.web.actions

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.State
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LivenessCheckAction : WebAction {
  @Inject private lateinit var services: MutableSet<Service>

  @Get("/_liveness")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun livenessCheck(): Response<String> {
    val isAlive = services.all {
      it.state() != State.FAILED && it.state() != State.TERMINATED
    }
    // TODO(jgulbronson) - Should return an empty body
    return Response(
        "",
        statusCode = if (isAlive) 200 else 503
    )
  }
}
