package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

// TODO(jwilson): rename this class from Request to FakeHttpCall.
data class FakeRequest(
  override val url: HttpUrl = HttpUrl.get("https://example.com/"),
  override val dispatchMechanism: DispatchMechanism = DispatchMechanism.GET,
  override val headers: Headers = Headers.of(),
  override var statusCode: Int = 200,
  val headersBuilder: Headers.Builder = Headers.Builder(),
  var requestBody: BufferedSource? = Buffer(),
  var responseBody: BufferedSink? = Buffer(),
  var webSocket: WebSocket? = null
) : Request {

  override val responseHeaders: Headers
    get() = headersBuilder.build()

  override fun setResponseHeader(name: String, value: String) {
    headersBuilder.set(name, value)
  }

  override fun addResponseHeaders(headers: Headers) {
    headersBuilder.addAll(headers)
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
}