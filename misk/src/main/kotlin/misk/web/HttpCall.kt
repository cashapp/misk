package misk.web

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.mediatype.MediaRange
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.BufferedSource

/**
 * Information about the socket on which a HTTP call arrived.
 */
sealed class SocketAddress {
  class Network(val ipAddress: String, val port: Int) : SocketAddress()
  class Unix(val path: String) : SocketAddress()
}

/**
 * A live HTTP call from a client for use by a chain of network interceptors.
 */
interface HttpCall {

  /** Immutable information about the incoming HTTP request. */
  val url: HttpUrl
  val linkLayerLocalAddress: SocketAddress?
  val dispatchMechanism: DispatchMechanism
  val requestHeaders: Headers

  /** The HTTP response under construction. */
  var statusCode: Int
  val responseHeaders: Headers

  fun setResponseHeader(name: String, value: String)
  fun addResponseHeaders(headers: Headers)

  /**
   * Call this before the response body is written to make sure it is encoded in a way that'll
   * permit trailers to be sent. This will do chunked encoding for HTTP/1. For HTTP/2 trailers are
   * always permitted. It is an error to call this for web socket calls.
   */
  fun requireTrailers()

  /**
   * Add a trailer. This requires that [requireTrailers] was called before the response body is
   * written.
   */
  fun setResponseTrailer(name: String, value: String)

  /**
   * Claim ownership of the request body stream. Returns null if the stream has already been
   * claimed. Callers should read the HTTP request body or call [putRequestBody] to create a new
   * chain with a request body that is unclaimed.
   */
  fun takeRequestBody(): BufferedSource?

  /**
   * Changes this call so that the next call to [takeRequestBody] returns [requestBody]. Use this
   * to apply filters such as decompression or metrics.
   *
   * This may only be called on calls whose request body has been taken. Otherwise that would be
   * leaked.
   */
  fun putRequestBody(requestBody: BufferedSource)

  /**
   * Claim ownership of the response body stream. Returns null if the stream has already been
   * claimed. Callers should write the HTTP response body or call [putRequestBody] to create a new
   * chain with a response body that is unclaimed.
   */
  fun takeResponseBody(): BufferedSink?

  /**
   * Changes this call so that the next call to [takeResponseBody] returns [responseBody]. Use this
   * to apply filters such as decompression or metrics.
   *
   * This may only be called on calls whose response body has been taken. Otherwise that would be
   * leaked.
   */
  fun putResponseBody(responseBody: BufferedSink)

  /** Claim ownership of the call's web socket. */
  fun takeWebSocket(): WebSocket?

  /**
   * Changes this call so that the next call to [takeWebSocket] returns [webSocket]. Use this to
   * apply filters such as decompression or metrics.
   *
   * This may only be called on calls whose web socket has been taken. Otherwise that would be
   * leaked.
   */
  fun putWebSocket(webSocket: WebSocket)

  /**
   * Set the call's web socket listener. This should only be called once, and only for web socket
   * calls.
   */
  fun initWebSocketListener(webSocketListener: WebSocketListener)

  fun contentType(): MediaType? {
    val contentType = requestHeaders.get("Content-Type") ?: return null
    return contentType.toMediaTypeOrNull()
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
        .method(dispatchMechanism.method, okRequestBody)
        .headers(requestHeaders)
        .build()
  }
}
