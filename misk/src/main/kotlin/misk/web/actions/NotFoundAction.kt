package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotFoundAction @Inject constructor() : WebAction {
  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun notFound(@PathParam path: String): Response<ResponseBody> {
    return response(path)
  }

  companion object {
    fun response(path: String): Response<ResponseBody> {
      return Response(
          body = "Nothing found at /$path".toResponseBody(),
          headers = Headers.of("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
          statusCode = 404
      )
    }
  }
}
