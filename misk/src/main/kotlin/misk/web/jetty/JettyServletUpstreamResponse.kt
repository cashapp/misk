package misk.web.jetty

import misk.web.ServletHttpCall
import misk.web.actions.WebSocketListener
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.server.Response
import java.util.function.Supplier

internal class JettyServletUpstreamResponse(
  val response: Response
) : ServletHttpCall.UpstreamResponse {
  var sendTrailers = false
  var trailers = headersOf()

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

    // Set the callback that'll return trailers at the end of the response body.
    response.trailers = Supplier<HttpFields> {
      val httpFields = HttpFields()
      for (i in 0 until trailers.size) {
        httpFields.add(trailers.name(i), trailers.value(i))
      }
      httpFields
    }
  }

  override fun setTrailer(name: String, value: String) {
    check(sendTrailers)
    trailers = trailers.newBuilder()
        .set(name, value)
        .build()
  }

  override fun initWebSocketListener(webSocketListener: WebSocketListener) =
      error("no web socket listeners for servlets")
}
