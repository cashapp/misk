package misk.web.jetty

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.HttpURLConnection
import java.net.ProtocolException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import misk.logging.getLogger
import misk.web.BoundAction
import misk.web.DispatchMechanism
import misk.web.ServletHttpCall
import misk.web.SocketAddress
import misk.web.WebConfig
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.actions.WebActionFactory
import misk.web.mediatype.MediaTypes
import misk.web.metadata.webaction.WebActionMetadata
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import org.eclipse.jetty.http.BadMessageException
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.unixdomain.server.UnixDomainServerConnector
import org.eclipse.jetty.unixsocket.server.UnixSocketConnector
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory

@Singleton
internal class WebActionsServlet
@Inject
constructor(
  webActionFactory: WebActionFactory,
  webActionEntries: List<WebActionEntry>,
  private val webConfig: WebConfig,
) : JettyWebSocketServlet() {

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
    // Check http2 is enabled if any gRPC actions are bound.
    if (boundActions.any { it.action.dispatchMechanism == DispatchMechanism.GRPC }) {
      val isHttp2Enabled =
        webConfig.http2 ||
          webConfig.unix_domain_socket?.h2c ?: false ||
          webConfig.unix_domain_sockets?.any { it.h2c ?: false } ?: false
      if (!isHttp2Enabled) {
        log.warn {
          "HTTP/2 must be enabled either via a unix domain socket or HTTP listener if any " +
            "gRPC actions are bound. This will cause an error in the future. " +
            "Check these actions: " +
            "${
              boundActions
                .filter { it.action.dispatchMechanism == DispatchMechanism.GRPC }
                .map { it.action.name }
            }"
        }
      }
    }
  }

  override fun service(request: HttpServletRequest, response: HttpServletResponse) {
    if (request.method == "PATCH") {
      doPatch(request, response)
      return
    }
    try {
      super.service(request, response)
    } catch (e: Throwable) {
      handleThrowable(request, response, e)
    }
  }

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  private fun doPatch(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  private fun handleCall(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      val responseBody = response.outputStream.sink().buffer()
      val dispatchMechanism = request.dispatchMechanism() ?: return sendNotFound(request, response, responseBody)

      val httpCall =
        ServletHttpCall.create(
          request = request,
          linkLayerLocalAddress = extractLinkLayerLocalAddress(request),
          dispatchMechanism = dispatchMechanism,
          upstreamResponse =
            if (response is Response) {
              JettyServletUpstreamResponse(response)
            } else {
              GenericServletUpstreamResponse(response)
            },
          requestBody = request.inputStream.source().buffer(),
          responseBody = responseBody,
        )

      val requestContentType = httpCall.contentType()
      val requestAccepts = httpCall.accepts()

      val candidateActions =
        boundActions.mapNotNull {
          it.match(httpCall.dispatchMechanism, requestContentType, requestAccepts, httpCall.url)
        }
      val bestAction = candidateActions.minOrNull()

      if (bestAction != null) {
        return bestAction.action.scopeAndHandle(request, httpCall, bestAction.pathMatcher)
      }

      // We didn't match with an action, so it's a 404. We hit this for things other than get/post
      // which are covered by the NotFoundAction.
      sendNotFound(request, response, responseBody)
    } catch (e: Throwable) {
      handleThrowable(request, response, e)
    }
  }

  private fun handleThrowable(request: HttpServletRequest, response: HttpServletResponse, throwable: Throwable) {
    log.error(throwable) { "Uncaught exception on ${request.dispatchMechanism()} ${request.httpUrl()}" }

    when (throwable) {
      is BadMessageException -> {
        response.status = HttpURLConnection.HTTP_BAD_REQUEST
        if (throwable.message != null) {
          response.writer.append(throwable.message)
        }
      }

      else -> {
        response.status = HttpURLConnection.HTTP_INTERNAL_ERROR
      }
    }
    response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
    response.writer.close()
  }

  private fun sendNotFound(request: HttpServletRequest, response: HttpServletResponse, responseBody: BufferedSink) {
    response.status = HttpURLConnection.HTTP_NOT_FOUND
    response.addHeader("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
    responseBody.writeUtf8("Nothing found at ${request.method} ${request.httpUrl()}")
    responseBody.close()
  }

  override fun configure(factory: JettyWebSocketServletFactory) {
    factory.setCreator(JettyWebSocket.Creator(boundActions))
    // Set idle timeout for WebSocket connections from config
    factory.idleTimeout = java.time.Duration.ofSeconds(webConfig.websocket_idle_timeout_seconds)
  }
}

internal fun HttpServletRequest.contentType() = contentType?.toMediaTypeOrNull()

internal fun JettyServerUpgradeResponse.headers(): Headers {
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
      result.addUnsafeNonAscii(name, value)
    }
  }
  return result.build()
}

internal fun HttpServletResponse.headers(): Headers {
  val result = Headers.Builder()
  for (name in headerNames) {
    for (value in getHeaders(name)) {
      result.addUnsafeNonAscii(name, value)
    }
  }
  return result.build()
}

internal fun HttpServletRequest.httpUrl(): HttpUrl {
  val rUrl = requestURL.replaceFirst(Regex("^ws://"), "http://")
  return if (queryString == null) {
    rUrl.toHttpUrl()
  } else {
    "$rUrl?$queryString".toHttpUrl()
  }
}

/** @throws ProtocolException on unexpected methods. */
internal fun HttpServletRequest.dispatchMechanism(): DispatchMechanism? {
  return when (method) {
    HttpMethod.GET.name -> DispatchMechanism.GET
    HttpMethod.POST.name ->
      when (contentType()) {
        MediaTypes.APPLICATION_GRPC_MEDIA_TYPE,
        MediaTypes.APPLICATION_GRPC_PROTOBUF_MEDIA_TYPE -> DispatchMechanism.GRPC
        else -> DispatchMechanism.POST
      }

    HttpMethod.PATCH.name -> DispatchMechanism.PATCH
    HttpMethod.PUT.name -> DispatchMechanism.PUT
    HttpMethod.DELETE.name -> DispatchMechanism.DELETE
    else -> null
  }
}

/** Extracts socket address information from an HttpServletRequest if available. */
private fun extractLinkLayerLocalAddress(request: HttpServletRequest): SocketAddress? {
  val jettyRequest = request as? Request ?: return null
  val httpChannel = jettyRequest.httpChannel ?: return null
  val connector = httpChannel.connector ?: return null

  return when (connector) {
    is UnixDomainServerConnector -> SocketAddress.Unix(connector.unixDomainPath.toString())

    is UnixSocketConnector -> SocketAddress.Unix(connector.unixSocket)

    is ServerConnector ->
      SocketAddress.Network(httpChannel.endPoint.remoteAddress.address.hostAddress, connector.localPort)

    else -> throw IllegalStateException("Unknown socket connector.")
  }
}
