package misk.web

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.mediatype.MediaRange
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

  /** HTTP request headers that may be modified via interception. */
  var requestHeaders: Headers

  /** Meaningful HTTP status about what actually happened. Not sent over the wire in the case
   * of gRPC, which always returns HTTP 200 even for errors. */
  var statusCode: Int

  /** The HTTP status code actually sent over the network. For gRPC, this is always 200, even
   * for errors, per the spec. **/
  val networkStatusCode: Int

  val responseHeaders: Headers

  /** Set both the raw network status code and the meaningful status code that's
   * recorded in metrics */
  fun setStatusCodes(statusCode: Int, networkStatusCode: Int)

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
    val contentType = requestHeaders["Content-Type"] ?: return null
    return contentType.toMediaTypeOrNull()
  }

  /**
   * Set or replaces an existing HTTP request header.
   */
  fun computeRequestHeader(name: String, computeFn: (String?) -> Pair<String, String>?) {
    val newHeader = computeFn(requestHeaders[name])
    val builder = requestHeaders.newBuilder().removeAll(name)
    requestHeaders = if (newHeader == null) {
      builder.build()
    } else {
      builder.add(newHeader.first, newHeader.second).build()
    }
  }

  fun accepts(): List<MediaRange> {
    // If no media types are valid we'll use MediaRange.ALL_MEDIA.
    val accepts = requestHeaders.values("Accept").flatMap {
      MediaRange.parseRanges(it, swallowExceptions = true)
    }

    return accepts.ifEmpty {
      listOf(MediaRange.ALL_MEDIA)
    }
  }
  fun asOkHttpRequest(): okhttp3.Request {
    // TODO(adrw) https://github.com/square/misk/issues/279
    val okRequestBody = when (dispatchMechanism) {
      DispatchMechanism.GET -> null
      DispatchMechanism.WEBSOCKET -> null
      else -> {
        val requestBody = takeRequestBody()!!
        if (requestBody.request(MAX_BUFFERED_REQUEST_BODY_BYTES)) {
          // If we were able to successfully buffer this much, this request might be huge! Let's
          // just stream it. (This is one-shot, if the call breaks for any reason it cannot be
          // retried later!)
          object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun isOneShot() = true // Don't stream the request body 2x.

            override fun writeTo(sink: BufferedSink) {
              sink.writeAll(requestBody)
            }
          }

        } else {
          // This request is short. Let's not stream it. That way we can retry if the HTTP request
          // out fails.
          requestBody.readByteString().toRequestBody()
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

/** 1 MiB. */
private const val MAX_BUFFERED_REQUEST_BODY_BYTES: Long = 1L * 1024 * 1024
