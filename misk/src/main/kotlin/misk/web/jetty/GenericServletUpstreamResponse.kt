package misk.web.jetty

import misk.web.ServletHttpCall
import misk.web.actions.WebSocketListener
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import javax.servlet.http.HttpServletResponse

/**
 * A generic implementation of ServletHttpCall.UpstreamResponse that works with
 * standard HttpServletResponse instead of requiring Jetty's specific Response class.
 */
internal class GenericServletUpstreamResponse(
  private val response: HttpServletResponse
) : ServletHttpCall.UpstreamResponse {
  private var sendTrailers = false
  private var trailers = headersOf()

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
    for (i in 0 until headers.size) {
      response.addHeader(headers.name(i), headers.value(i))
    }
  }

  override fun requireTrailers() {
    sendTrailers = true

    response.setTrailerFields({
      val trailerMap = mutableMapOf<String, String>()
      for (i in 0 until trailers.size) {
        trailerMap[trailers.name(i)] = trailers.value(i)
      }
      trailerMap
    })
  }

  override fun setTrailer(name: String, value: String) {
    check(sendTrailers)
    trailers = trailers.newBuilder()
      .set(name, value)
      .build()
  }

  override fun initWebSocketListener(webSocketListener: WebSocketListener) =
    error("no web socket listeners for generic servlets")
}
