package misk.web

import okhttp3.Headers
import okhttp3.Response
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

fun misk.web.Response<ResponseBody>.writeToJettyResponse(jettyResponse: HttpServletResponse) {
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

fun Response.toMisk() : misk.web.Response<*> {
  val miskBody : ResponseBody = object: ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.writeAll(body()!!.source())
    }
  }
  return misk.web.Response(miskBody, this.headers(), this.code())
}