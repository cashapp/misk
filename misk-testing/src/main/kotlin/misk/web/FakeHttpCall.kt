package misk.web

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

data class FakeHttpCall(
  override val url: HttpUrl = "https://example.com/".toHttpUrl(),
  override val linkLayerLocalAddress: SocketAddress = SocketAddress.Network("1.2.3.4", 56789),
  override val dispatchMechanism: DispatchMechanism = DispatchMechanism.GET,
  override var requestHeaders: Headers = headersOf(),
  override var statusCode: Int = 200,
  override var networkStatusCode: Int = 200,
  val headersBuilder: Headers.Builder = Headers.Builder(),
  var sendTrailers: Boolean = false,
  val trailersBuilder: Headers.Builder = Headers.Builder(),
  var requestBody: BufferedSource? = Buffer(),
  var responseBody: BufferedSink? = Buffer(),
  var webSocket: WebSocket? = null,
  var webSocketListener: WebSocketListener? = null
) : HttpCall {

  override val responseHeaders: Headers
    get() = headersBuilder.build()

  override fun setStatusCodes(statusCode: Int, networkStatusCode: Int) {
    this.statusCode = statusCode
    this.networkStatusCode = networkStatusCode
  }

  override fun setResponseHeader(name: String, value: String) {
    headersBuilder.set(name, value)
  }

  override fun addResponseHeaders(headers: Headers) {
    headersBuilder.addAll(headers)
  }

  override fun requireTrailers() {
    check(dispatchMechanism != DispatchMechanism.WEBSOCKET)
    sendTrailers = true
  }

  override fun setResponseTrailer(name: String, value: String) {
    check(sendTrailers)
    trailersBuilder.set(name, value)
  }

  override fun takeRequestBody(): BufferedSource? {
    val result = requestBody
    requestBody = null
    return result
  }

  override fun putRequestBody(requestBody: BufferedSource) {
    check(this.requestBody == null) { "previous request body leaked; take it first" }
    this.requestBody = requestBody
  }

  override fun takeResponseBody(): BufferedSink? {
    val result = responseBody
    responseBody = null
    return result
  }

  override fun putResponseBody(responseBody: BufferedSink) {
    check(this.responseBody == null) { "previous response body leaked; take it first" }
    this.responseBody = responseBody
  }

  override fun takeWebSocket(): WebSocket? {
    val result = webSocket
    webSocket = null
    return result
  }

  override fun putWebSocket(webSocket: WebSocket) {
    check(this.webSocket == null) { "previous web socket leaked; take it first" }
    this.webSocket = webSocket
  }

  override fun initWebSocketListener(webSocketListener: WebSocketListener) {
    check(this.webSocketListener == null) { "web socket listener already set" }
    this.webSocketListener = webSocketListener
  }
}
