package misk.web.actions

import misk.logging.getLogger
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
import okhttp3.Headers.Companion.headersOf
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
    private val logger = getLogger<NotFoundAction>()

    fun response(path: String): Response<ResponseBody> {
      logger.info("Nothing found at /$path")
      return Response(
          body = "Nothing found at /$path".toResponseBody(),
          headers = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
          statusCode = 404
      )
    }
  }
}
