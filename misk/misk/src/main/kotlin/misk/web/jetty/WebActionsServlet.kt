package misk.web.jetty

import misk.exceptions.StatusCode
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.web.BoundAction
import misk.web.DispatchMechanism
import misk.web.HttpCall
import misk.web.ServletHttpCall
import misk.web.ServletHttpCall.UpstreamResponse
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.actions.WebActionFactory
import misk.web.actions.WebActionMetadata
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okio.buffer
import okio.sink
import okio.source
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
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
      val httpCall = ServletHttpCall.create(
          request = request,
          dispatchMechanism = request.dispatchMechanism(),
          upstreamResponse = callback(response),
          requestBody = request.inputStream.source().buffer(),
          responseBody = response.outputStream.sink().buffer()
      )

      val seedData = mapOf(
          keyOf<HttpServletRequest>() to request,
          keyOf<HttpCall>() to httpCall)

      val requestContentType = httpCall.contentType()
      val requestAccepts = httpCall.accepts()
      scope.enter(seedData).use {
        val candidateActions = boundActions.mapNotNull {
          it.match(httpCall.dispatchMechanism, requestContentType, requestAccepts, httpCall.url)
        }

        val bestAction = candidateActions.sorted().firstOrNull()
        if (bestAction != null) {
          bestAction.handle(httpCall, response)
          return
        }
      }
    } catch (e: ProtocolException) {
      // Probably an unexpected HTTP method. Send a 404 below.
    }

    response.status = StatusCode.NOT_FOUND.code
    response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
    response.writer.print("Nothing found at ${request.httpUrl()}")
    response.writer.close()
  }

  override fun configure(factory: WebSocketServletFactory) {
    factory.creator = WebSocketCreator { servletUpgradeRequest, upgradeResponse ->
      val realWebSocket = RealWebSocket()
      val httpCall = ServletHttpCall.create(
          request = servletUpgradeRequest.httpServletRequest,
          dispatchMechanism = DispatchMechanism.WEBSOCKET,
          upstreamResponse = callback(upgradeResponse),
          webSocket = realWebSocket
      )

      val candidateActions = boundActions.mapNotNull {
        it.match(DispatchMechanism.WEBSOCKET, null, listOf(), httpCall.url)
      }

      val bestAction = candidateActions.sorted().firstOrNull() ?: return@WebSocketCreator null
      val webSocketListener = bestAction.handleWebSocket(httpCall)
      realWebSocket.listener = webSocketListener
      realWebSocket.adapter
    }
  }
}

private fun callback(response: ServletUpgradeResponse): UpstreamResponse {
  return object : UpstreamResponse {
    override var statusCode: Int
      get() = response.statusCode
      set(value) {
        response.statusCode = value
      }

    override val headers: Headers
      get() = response.headers()

    override fun setHeader(name: String, value: String) {
      response.setHeader(name, value)
    }

    override fun addHeaders(headers: Headers) {
      for (i in 0 until headers.size()) {
        response.addHeader(headers.name(i), headers.value(i))
      }
    }
  }
}

private fun callback(response: HttpServletResponse): UpstreamResponse {
  return object : UpstreamResponse {
    override var statusCode: Int
      get() = response.status
      set(value) {
        response.status = value
      }

    override val headers: Headers
      get() = response.headers()

    override fun setHeader(name: String, value: String) {
      response.setHeader(name, value)
    }

    override fun addHeaders(headers: Headers) {
      for (i in 0 until headers.size()) {
        response.addHeader(headers.name(i), headers.value(i))
      }
    }
  }
}

internal fun HttpServletRequest.contentType() = contentType?.let { MediaType.parse(it) }

internal fun ServletUpgradeResponse.headers(): Headers {
  val result = Headers.Builder()
  for (name in headerNames) {
    for (value in getHeaders(name)) {
      result.add(name, value)
    }
  }
  return result.build()
}

internal fun HttpServletRequest.headers(): Headers {
  val result = Headers.Builder()
  for (name in headerNames) {
    for (value in getHeaders(name)) {
      result.add(name, value)
    }
  }
  return result.build()
}

internal fun HttpServletResponse.headers(): Headers {
  val result = Headers.Builder()
  for (name in headerNames) {
    for (value in getHeaders(name)) {
      result.add(name, value)
    }
  }
  return result.build()
}

internal fun HttpServletRequest.httpUrl(): HttpUrl {
  return if (queryString == null) {
    HttpUrl.get("$requestURL")
  } else {
    HttpUrl.get("$requestURL?$queryString")
  }
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
