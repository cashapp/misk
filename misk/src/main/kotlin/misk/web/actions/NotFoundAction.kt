package misk.web.actions

import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Singleton

@Singleton
class NotFoundAction : WebAction {
  @Get("/{path:.*}")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun notFound(@PathParam path: String): Response<String> {
    return Response(
        body = "Nothing found at /$path",
        statusCode = 404
    )
  }
}
