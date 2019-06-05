package misk.web

import misk.web.actions.WebSocket
import misk.web.jetty.headers
import misk.web.jetty.httpUrl
import okhttp3.Headers
import okhttp3.HttpUrl
import okio.BufferedSink
import okio.BufferedSource
import javax.servlet.http.HttpServletRequest

internal data class ServletHttpCall(
  override val url: HttpUrl,
  override val dispatchMechanism: DispatchMechanism,
  override val headers: Headers,
  var requestBody: BufferedSource? = null,
  val upstreamResponse: UpstreamResponse,
  var responseBody: BufferedSink? = null,
  var webSocket: WebSocket? = null
) : Request {

  override var statusCode: Int
    get() = upstreamResponse.statusCode
    set(value) {
      upstreamResponse.statusCode = value
    }

  override val responseHeaders: Headers
    get() = upstreamResponse.headers

  override fun setResponseHeader(name: String, value: String) {
    upstreamResponse.setHeader(name, value)
  }

  override fun addResponseHeaders(headers: Headers) {
    upstreamResponse.addHeaders(headers)
  }

  override fun takeRequestBody(): BufferedSource? {
    val result = requestBody
    requestBody = null
    return result
  }

  override fun takeResponseBody(): BufferedSink? {
    val result = responseBody
    responseBody = null
    return result
  }

  override fun takeWebSocket(): WebSocket? {
    val result = webSocket
    webSocket = null
    return result
  }

  override fun withRequestBody(requestBody: BufferedSource) = copy(requestBody = requestBody)

  override fun withResponseBody(responseBody: BufferedSink) = copy(responseBody = responseBody)

  override fun withWebSocket(webSocket: WebSocket) = copy(webSocket = webSocket)

  /** Adapts the underlying servlet call to send data to the client. */
  interface UpstreamResponse {
    var statusCode: Int
    val headers: Headers
    fun setHeader(name: String, value: String)
    fun addHeaders(headers: Headers)
  }

  companion object {
    fun create(
      request: HttpServletRequest,
      dispatchMechanism: DispatchMechanism,
      upstreamResponse: UpstreamResponse,
      webSocket: WebSocket? = null,
      requestBody: BufferedSource? = null,
      responseBody: BufferedSink? = null
    ): ServletHttpCall {
      if (dispatchMechanism == DispatchMechanism.WEBSOCKET) {
        check(webSocket != null)
      }

      return ServletHttpCall(
          url = request.httpUrl(),
          dispatchMechanism = dispatchMechanism,
          headers = request.headers(),
          upstreamResponse = upstreamResponse,
          requestBody = requestBody,
          responseBody = responseBody,
          webSocket = webSocket
      )
    }
  }
}