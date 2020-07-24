package misk.web.actions

import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.ConcurrencyLimitsOptOut
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.Post
import misk.web.ConcurrencyLimitsOptOut
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.jetty.WebActionsServlet
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NotFoundAction @Inject constructor(
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>
) : WebAction {
  @Inject internal lateinit var servletProvider: Provider<WebActionsServlet>

  @Get("/{path:.*}")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @Post("/{path:.*}")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun notFound(@PathParam path: String): Response<ResponseBody> {
    return Response(
        body = notFoundMessage(path, clientHttpCall.get()).toResponseBody(),
        headers = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
        statusCode = 404
    )
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

  /** Generates a message for a client that hints what may have been wrong with their request. */
  private fun notFoundMessage(path: String, httpCall: HttpCall) = buildString {
    val boundActions = servletProvider.get().boundActions
    val alternativeActions = boundActions.mapNotNull {
      it.matchByUrl(httpCall.url)
    }.filterNot { it.action.pathPattern.matchesWildcardPath }

    append("Nothing found at /$path.\n\n")
    append("Received:\n")
    append("${httpCall.dispatchMechanism.method} /$path\n")
    for (accept in httpCall.accepts()) {
      append("Accept: $accept\n")
    }
    val contentType = httpCall.contentType()
    if (contentType != null) {
      append("Content-Type: ${httpCall.contentType()}\n")
    }

    for (i in 0 until alternativeActions.size) {
      append("\n")
      val alternative = alternativeActions[i]
      val metadata = alternative.action.metadata
      append("Alternative:\n")
      append("${metadata.httpMethod} ${metadata.pathPattern}\n")
      append("Accept: ${metadata.responseMediaType}\n")
      for (requestContentType in metadata.requestMediaTypes) {
        append("Content-Type: $requestContentType\n")
      }
    }
  }
}
