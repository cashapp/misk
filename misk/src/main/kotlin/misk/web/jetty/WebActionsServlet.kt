package misk.web.jetty

import misk.exceptions.StatusCode
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.web.BoundAction
import misk.web.Request
import misk.web.actions.WebAction
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okio.Buffer
import okio.Okio
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.eclipse.jetty.websocket.servlet.WebSocketServlet
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Singleton
internal class WebActionsServlet @Inject constructor(
  private val boundActions: MutableSet<BoundAction<out WebAction, *>>,
  private val scope: ActionScope
) : WebSocketServlet() {
  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  private fun handleCall(request: HttpServletRequest, response: HttpServletResponse) {
    val asRequest = request.asRequest()
    val seedData = mapOf(
        keyOf<HttpServletRequest>() to request,
        keyOf<Request>() to asRequest)

    scope.enter(seedData).use {
          val candidateActions = boundActions.mapNotNull {
            it.match(
                request.method,
                request.contentType?.let { MediaType.parse(it) },
                request.accepts(),
                asRequest.url
            )
          }.sorted()

          val bestAction = candidateActions.firstOrNull()
          if (bestAction != null) {
            bestAction.handle(asRequest, response)
          } else {
            response.status = StatusCode.NOT_FOUND.code
            response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
            response.writer.print("Nothing found at /${asRequest.url.encodedPath()}")
            response.writer.close()
          }
        }
  }

  override fun configure(factory: WebSocketServletFactory) {
    factory.creator = WebSocketCreator { servletUpgradeRequest, _ ->
      val realWebSocket = RealWebSocket()
      val request = servletUpgradeRequest.httpServletRequest
      val asRequest = Request(
          request.urlString()!!,
          HttpMethod.valueOf(request.method),
          request.headers(),
          Buffer(), // empty body
          realWebSocket
      )

      val candidateActions = boundActions.mapNotNull {
        it.match(
            request.method,
            null,
            listOf(),
            asRequest.url
        )
      }

      val bestAction = candidateActions.sorted().firstOrNull() ?: return@WebSocketCreator null
      val webSocketListener = bestAction.handleWebSocket(asRequest)
      realWebSocket.listener = webSocketListener
      realWebSocket.adapter
    }
  }
}

internal fun HttpServletRequest.headers(): Headers {
  val headersBuilder = Headers.Builder()
  val headerNames = headerNames
  for (headerName in headerNames) {
    val headerValues = getHeaders(headerName)
    for (headerValue in headerValues) {
      headersBuilder.add(headerName, headerValue)
    }
  }
  return headersBuilder.build()
}

private fun HttpServletRequest.urlString(): HttpUrl? {
  return if (queryString == null)
    HttpUrl.parse(requestURL.toString()) else
    HttpUrl.parse(requestURL.toString() + "?" + queryString)
}

private fun HttpServletRequest.asRequest(): Request {
  return Request(
      urlString()!!,
      HttpMethod.valueOf(method),
      headers(),
      Okio.buffer(Okio.source(inputStream))
  )
}

private fun HttpServletRequest.accepts(): List<MediaRange> {
  // TODO(mmihic): Don't blow up if one of the accept headers can't be parsed
  val accepts = getHeaders("Accept")?.toList()?.flatMap {
    MediaRange.parseRanges(it)
  }

  return if (accepts == null || accepts.isEmpty()) {
    listOf(MediaRange.ALL_MEDIA)
  } else {
    accepts
  }
}
