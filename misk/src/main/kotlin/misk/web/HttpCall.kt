package misk.web

import misk.web.actions.WebSocket
import misk.web.mediatype.MediaRange
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.BufferedSource

/**
 * A live HTTP call from a client for use by a chain of network interceptors.
 */
interface HttpCall {

  /** Immutable information about the incoming HTTP request. */
  val url: HttpUrl
  val dispatchMechanism: DispatchMechanism
  val requestHeaders: Headers

  /** The HTTP response under construction. */
  var statusCode: Int
  val responseHeaders: Headers

  fun setResponseHeader(name: String, value: String)
  fun addResponseHeaders(headers: Headers)

  /**
   * Claim ownership of the request body stream. Returns null if the stream has already been
   * claimed. Callers should read the HTTP request body or call [withRequestBody] to create a new
   * chain with a request body that is unclaimed.
   */
  fun takeRequestBody(): BufferedSource?

  /**
   * Claim ownership of the response body stream. Returns null if the stream has already been
   * claimed. Callers should write the HTTP response body or call [withRequestBody] to create a new
   * chain with a response body that is unclaimed.
   */
  fun takeResponseBody(): BufferedSink?

  /** Claim ownership of the call's web socket. */
  fun takeWebSocket(): WebSocket?

  /** Returns a new call with the request body unclaimed. */
  fun withRequestBody(requestBody: BufferedSource): HttpCall

  /** Returns a new call with the response body unclaimed. */
  fun withResponseBody(responseBody: BufferedSink): HttpCall

  /** Returns a new call with the web socket unclaimed. */
  fun withWebSocket(webSocket: WebSocket): HttpCall

  fun contentType(): MediaType? {
    val contentType = requestHeaders.get("Content-Type") ?: return null
    return MediaType.parse(contentType)
  }

  fun accepts(): List<MediaRange> {
    // TODO(mmihic): Don't blow up if one of the accept headers can't be parsed
    val accepts = requestHeaders.values("Accept").flatMap { MediaRange.parseRanges(it) }

    return if (accepts.isEmpty()) {
      listOf(MediaRange.ALL_MEDIA)
    } else {
      accepts
    }
  }

  fun asOkHttpRequest(): okhttp3.Request {
    // TODO(adrw) https://github.com/square/misk/issues/279
    val okRequestBody = when (dispatchMechanism) {
      DispatchMechanism.GET -> null
      DispatchMechanism.WEBSOCKET -> null
      else -> object : RequestBody() {
        override fun contentType(): MediaType? = null
        override fun writeTo(sink: BufferedSink) {
          sink.writeAll(takeRequestBody()!!)
        }
      }
    }

    return okhttp3.Request.Builder()
        .url(url)
        .method(dispatchMechanism.method.toString(), okRequestBody)
        .headers(requestHeaders)
        .build()
  }
}
