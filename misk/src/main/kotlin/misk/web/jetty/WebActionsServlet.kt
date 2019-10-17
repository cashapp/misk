package misk.web.jetty

import misk.exceptions.StatusCode
import misk.logging.getLogger
import misk.web.BoundAction
import misk.web.DispatchMechanism
import misk.web.ServletHttpCall
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.actions.WebActionFactory
import misk.web.mediatype.MediaTypes
import misk.web.metadata.WebActionMetadata
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.buffer
import okio.sink
import okio.source
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
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
  webActionEntries: List<WebActionEntry>
) : WebSocketServlet() {

  companion object {
    val log = getLogger<WebActionsServlet>()
  }

  internal val boundActions: MutableSet<BoundAction<out WebAction>> = mutableSetOf()

  internal val webActionsMetadata: List<WebActionMetadata> by lazy { boundActions.map { it.metadata } }

  init {
    for (entry in webActionEntries) {
      boundActions += webActionFactory.newBoundAction(entry.actionClass, entry.url_path_prefix)
    }
    // Verify no two Actions have identical routing annotations, which is most likely a bad
    // copy/paste error and results in unexpected results for a developer. This fails the service
    // startup, which should be caught with a unit test.
    for (action in boundActions) {
      for (other in boundActions) {
        check(action === other || !action.hasIdenticalRouting(other)) {
          "Actions [${action.action.name}, ${other.action.name}] have identical routing annotations."
        }
      }
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
          upstreamResponse = JettyServletUpstreamResponse(response as Response),
          requestBody = request.inputStream.source().buffer(),
          responseBody = response.outputStream.sink().buffer()
      )

      val requestContentType = httpCall.contentType()
      val requestAccepts = httpCall.accepts()

      val candidateActions = boundActions.mapNotNull {
        it.match(httpCall.dispatchMechanism, requestContentType, requestAccepts, httpCall.url)
      }
      val bestAction = candidateActions.sorted().firstOrNull()

      if (bestAction != null) {
        bestAction.action.scopeAndHandle(request, httpCall, bestAction.pathMatcher)
        return
      }
    } catch (_: ProtocolException) {
      // Probably an unexpected HTTP method. Send a 404 below.
    } catch (e: Throwable) {
      log.error(e) { "Uncaught exception on ${request.dispatchMechanism()} ${request.httpUrl()}" }

      response.status = StatusCode.INTERNAL_SERVER_ERROR.code
      response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
      response.writer.close()

      return
    }

    response.status = StatusCode.NOT_FOUND.code
    response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
    response.writer.print("Nothing found at ${request.httpUrl()}")
    response.writer.close()
  }

  override fun configure(factory: WebSocketServletFactory) {
    factory.creator = JettyWebSocket.Creator(boundActions)
  }
}

internal fun HttpServletRequest.contentType() = contentType?.toMediaTypeOrNull()

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
    "$requestURL".toHttpUrl()
  } else {
    "$requestURL?$queryString".toHttpUrl()
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
