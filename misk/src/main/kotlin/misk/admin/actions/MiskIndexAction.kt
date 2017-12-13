package misk.admin.actions

import misk.templating.TemplateRenderer
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.admin.AdminServiceLink
import okhttp3.Headers
import okio.BufferedSink
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiskIndexAction @Inject constructor(
    val servicesLink: MutableSet<AdminServiceLink>,
    val templateRenderer: TemplateRenderer
) : WebAction {
    @AdminAction
    @Get("/_admin/index.html")
    fun miskIndex(): Response<ResponseBody> {
        val templatePath = "misk/index.html"

        val body = object : ResponseBody {
            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8(templateRenderer.render(templatePath,
                        mapOf(Pair("servicesLink", servicesLink))))
            }
        }

        return Response(
                body = body,
                // TODO(jgulbronson) - Make @HtmlResponse annotation
                headers = Headers.of("Content-Type", "text/html; charset=utf-8"),
                statusCode = 200
        )
    }
}
