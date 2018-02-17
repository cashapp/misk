package misk.web.jetty

import misk.inject.keyOf
import misk.logging.getLogger
import misk.scope.ActionScope
import misk.web.BoundAction
import misk.web.Request
import misk.web.actions.WebAction
import okhttp3.Headers
import okhttp3.HttpUrl
import okio.Okio
import org.eclipse.jetty.http.HttpMethod
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val logger = getLogger<WebActionsServlet>()

@Singleton
internal class WebActionsServlet @Inject constructor(
    private val boundActions: MutableSet<BoundAction<out WebAction, *>>,
    private val scope: ActionScope
) : HttpServlet() {
  override fun doGet(
      request: HttpServletRequest,
      response: HttpServletResponse
  ) {
    handleCall(request, response)
  }

  override fun doPost(
      request: HttpServletRequest,
      response: HttpServletResponse
  ) {
    handleCall(request, response)
  }

  private fun handleCall(
      request: HttpServletRequest,
      response: HttpServletResponse
  ) {
    val asRequest = request.asRequest()
    val seedData = mapOf(
        keyOf<HttpServletRequest>() to request,
        keyOf<Request>() to asRequest
    )

    scope.enter(seedData)
        .use {
          val candidateActions = boundActions.mapNotNull { it.match(request, asRequest.url) }
          val bestAction = candidateActions.sorted()
              .firstOrNull()
          bestAction?.handle(asRequest, response)
          logger.debug { "Request handled by WebActionServlet" }
        }
  }
}

internal fun HttpServletRequest.asRequest(): Request {
  val urlString = requestURL.toString() +
      if (queryString != null) "?" + queryString
      else ""

  val headersBuilder = Headers.Builder()
  val headerNames = headerNames
  for (headerName in headerNames) {
    val headerValues = getHeaders(headerName)
    for (headerValue in headerValues) {
      headersBuilder.add(headerName, headerValue)
    }
  }
  return Request(
      HttpUrl.parse(urlString)!!,
      HttpMethod.valueOf(method),
      headersBuilder.build(),
      Okio.buffer(Okio.source(inputStream))
  )
}
