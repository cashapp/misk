package misk.web.actions

import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.BoundActionMatch
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.jetty.WebActionsServlet
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NotFoundAction @Inject internal constructor(
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
  private val servletProvider: Provider<WebActionsServlet>
) : WebAction {
  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated
  fun notFound(@PathParam path: String): Response<ResponseBody> {
    val httpCall = clientHttpCall.get()
    val httpMethod = httpCall.dispatchMechanism.method
    val alternativeActions = urlOnlyMatches(httpCall)
    // If there are any actions that match on URL the client is likely using the wrong
    // content type or the wrong method, so we'll return a 405 (wrong method) or
    // 415 (wrong media type).
    val statusCode = when {
      alternativeActions.isEmpty() -> 404
      alternativeActions.none { it.action.metadata.httpMethod == httpMethod } -> 405
      else -> 415
    }

    return Response(
      body = notFoundMessage(path, alternativeActions, httpCall).toResponseBody(),
      headers = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
      statusCode = statusCode
    )
  }

  /** Generates a message for a client that hints what may have been wrong with their request. */
  private fun notFoundMessage(
    path: String,
    alternativeActions: List<BoundActionMatch>,
    httpCall: HttpCall
  ) = buildString {
    append("Nothing found at /$path.\n\n")
    append("Received:\n")
    append("${httpCall.dispatchMechanism.method} /$path\n")
    for (accept in httpCall.accepts()) {
      append("Accept: $accept\n")
    }
    val contentType = httpCall.contentType()
    if (contentType != null) {
      append("Content-Type: $contentType\n")
    }

    for (element in alternativeActions) {
      append("\n")
      val metadata = element.action.metadata
      append("Alternative:\n")
      append("${metadata.httpMethod} ${metadata.pathPattern}\n")
      append("Accept: ${metadata.responseMediaType}\n")
      for (requestContentType in metadata.requestMediaTypes) {
        append("Content-Type: $requestContentType\n")
      }
    }
  }

  private fun urlOnlyMatches(httpCall: HttpCall): List<BoundActionMatch> {
    val boundActions = servletProvider.get().boundActions
    return boundActions.mapNotNull {
      it.matchByUrl(httpCall.url)
    }.filterNot { it.action.pathPattern.matchesWildcardPath }
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
