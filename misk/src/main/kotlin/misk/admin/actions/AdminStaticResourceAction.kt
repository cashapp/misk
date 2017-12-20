package misk.admin.actions

import misk.resources.ResourceLoader
import misk.templating.TemplateRenderer
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import okhttp3.Headers
import okio.BufferedSink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves static CSS, HTML, and Javascript files for admin pages with no caching using the
 * [ResourceLoader].
 */
@Singleton
class AdminStaticResourceAction @Inject constructor(
    val templateRenderer: TemplateRenderer
) : WebAction {
    @AdminAction
    @Get("/_admin/{resourcePath:.*}.{fileType:(css|html|js)}")
    fun staticResource(resourcePath: String, fileType: String): Response<ResponseBody> {
        val fullResourcePath = "$fileType/$resourcePath.$fileType"
        val resourceSource = ResourceLoader.open(fullResourcePath) ?:
                throw IllegalArgumentException("Could not find resource at $fullResourcePath")

        val body = object : ResponseBody {
            override fun writeTo(sink: BufferedSink) {
                // Only render as a template if the file type is HTML
                if (fileType == "html") {
                    sink.writeUtf8(templateRenderer.render(fullResourcePath))
                } else {
                    sink.writeUtf8(resourceSource.readUtf8())
                }
            }
        }

        val mimeType = when (fileType) {
            "css" -> "text/css"
            "html" -> "text/html"
            "js" -> "text/javascript"
            else -> throw IllegalArgumentException("File type must be one of css, html, js")
        }

        return Response(
                body = body,
                headers = Headers.of("Content-Type", "$mimeType; charset=utf-8"),
                statusCode = 200
        )
    }
}
