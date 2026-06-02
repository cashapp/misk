package misk.web.v2

import jakarta.inject.Inject
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.HttpCall
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers

internal class DashboardV2RedirectAction
@Inject
constructor(@JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>) : WebAction {
  @Get("/v2/_admin/{rest:.*}")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun redirect(): Response<ResponseBody> {
    val httpCall = clientHttpCall.get()
    val rest =
      listOf(
          httpCall.url.encodedPath.removePrefix("/v2"),
          httpCall.url.encodedFragment,
          httpCall.url.encodedQuery?.let { "?$it" },
        )
        .filterNotNull()
        .joinToString("")
    return Response(
      body = "go to $rest".toResponseBody(),
      statusCode = HTTP_MOVED_TEMP,
      headers = Headers.headersOf("Location", rest),
    )
  }
}
