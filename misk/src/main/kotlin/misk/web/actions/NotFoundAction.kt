package misk.web.actions

import misk.web.Get
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okio.BufferedSink
import javax.inject.Singleton

@Singleton
class NotFoundAction : WebAction {
  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  fun notFound(@PathParam path: String): Response<ResponseBody> {
    return Response(
        body = object : ResponseBody {
          override fun writeTo(sink: BufferedSink) {
            sink.writeString("Nothing found at /$path", Charsets.UTF_8)
          }
        },
        headers = Headers.of("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
        statusCode = 404
    )
  }
}
