package misk.web.actions

import misk.web.Get
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import javax.inject.Singleton

@Singleton
class NotFoundAction : WebAction {
    @Get("/{path:.*}")
    @Post("/{path:.*}")
    @RequestContentType(MediaTypes.ALL)
    @ResponseContentType(MediaTypes.ALL)
    fun notFound(@PathParam path: String): Response<String> {
        return Response(
                body = "Nothing found at /$path",
                headers = Headers.of("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
                statusCode = 404
        )
    }
}
