package misk.web

import okhttp3.Headers
import okio.BufferedSink
import okio.Okio
import javax.servlet.http.HttpServletResponse

/** An HTTP response body, headers, and status code. */
data class Response<out T>(
    val body: T,
    val headers: Headers = Headers.of(),
    val statusCode: Int = 200
)

interface ResponseBody {
  fun writeTo(sink: BufferedSink)
}

fun Response<ResponseBody>.writeToJettyResponse(jettyResponse: HttpServletResponse) {
  jettyResponse.status = statusCode

  for ((key, values) in headers.toMultimap()) {
    for (value in values) {
      jettyResponse.addHeader(key, value)
    }
  }

  val sink = jettyResponse.bufferedSink()
  body.writeTo(sink)
  sink.emit()
}

private fun HttpServletResponse.bufferedSink() = Okio.buffer(Okio.sink(outputStream))
