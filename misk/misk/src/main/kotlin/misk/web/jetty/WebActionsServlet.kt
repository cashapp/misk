package misk.web.jetty

import misk.exceptions.StatusCode
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.web.BoundAction
import misk.web.DispatchMechanism
import misk.web.Request
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.actions.WebActionFactory
import misk.web.actions.WebActionMetadata
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okio.Buffer
import okio.buffer
import okio.source
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.eclipse.jetty.websocket.servlet.WebSocketServlet
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import java.net.ProtocolException
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Singleton
internal class WebActionsServlet @Inject constructor(
  webActionFactory: WebActionFactory,
  webActionEntries: List<WebActionEntry>,
  private val scope: ActionScope
) : WebSocketServlet() {

  private val boundActions: MutableSet<BoundAction<out WebAction>> = mutableSetOf()

  internal val webActionsMetadata: List<WebActionMetadata> by lazy { boundActions.map { it.metadata } }

  init {
    for (entry in webActionEntries) {
      boundActions += webActionFactory.newBoundAction(entry.actionClass, entry.url_path_prefix)
    }
  }

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  private fun handleCall(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      val asRequest = request.asRequest()
      val seedData = mapOf(
          keyOf<HttpServletRequest>() to request,
          keyOf<Request>() to asRequest)

      val requestContentType = request.contentType()
      val requestAccepts = request.accepts()
      scope.enter(seedData).use {
        val candidateActions = boundActions.mapNotNull {
          it.match(asRequest.dispatchMechanism, requestContentType, requestAccepts, asRequest.url)
        }

        val bestAction = candidateActions.sorted().firstOrNull()
        if (bestAction != null) {
          bestAction.handle(asRequest, response)
          return
        }
      }
    } catch (e: ProtocolException) {
      // Probably an unexpected HTTP method. Send a 404 below.
    }

    response.status = StatusCode.NOT_FOUND.code
    response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
    response.writer.print("Nothing found at ${request.urlString()}")
    response.writer.close()
  }

  override fun configure(factory: WebSocketServletFactory) {
    factory.creator = WebSocketCreator { servletUpgradeRequest, _ ->
      val realWebSocket = RealWebSocket()
      val request = servletUpgradeRequest.httpServletRequest
      val asRequest = Request(
          request.urlString()!!,
          DispatchMechanism.WEBSOCKET,
          request.headers(),
          Buffer(), // Empty body.
          realWebSocket
      )

      val candidateActions = boundActions.mapNotNull {
        it.match(DispatchMechanism.WEBSOCKET, null, listOf(), asRequest.url)
      }

      val bestAction = candidateActions.sorted().firstOrNull() ?: return@WebSocketCreator null
      val webSocketListener = bestAction.handleWebSocket(asRequest)
      realWebSocket.listener = webSocketListener
      realWebSocket.adapter
    }
  }
}

internal fun HttpServletRequest.contentType() = contentType?.let { MediaType.parse(it) }

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
      dispatchMechanism(),
      headers(),
      inputStream.source().buffer()
  )
}

/** @throws ProtocolException on unexpected methods. */
internal fun HttpServletRequest.dispatchMechanism(): DispatchMechanism {
  return when (method) {
    HttpMethod.GET.name -> DispatchMechanism.GET
    HttpMethod.POST.name -> when (contentType()) {
      MediaTypes.APPLICATION_GRPC_MEDIA_TYPE -> DispatchMechanism.GRPC
      else -> DispatchMechanism.POST
    }
    else -> throw ProtocolException("unexpected method: $method")
  }
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
