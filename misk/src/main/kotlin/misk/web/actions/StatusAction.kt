package misk.web.actions

import misk.healthchecks.HealthCheck
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import okio.BufferedSink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusAction : WebAction {
    @Inject lateinit var healthChecks: MutableSet<HealthCheck>

    @Get("/_status")
    fun statusCheck(): Response<ResponseBody> {
        val body = object : ResponseBody {
            override fun writeTo(sink: BufferedSink) {
                val template = File("misk/src/main/kotlin/misk/web/admin/status.html")
                sink.write(template.inputStream().readBytes())
            }
        }

        return Response(body)
    }
}
