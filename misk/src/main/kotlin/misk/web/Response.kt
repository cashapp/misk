package misk.web

import okhttp3.Headers
import okhttp3.HttpUrl
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
//  val okType = this.body()!!.contentType()

  val okBody = this.body()
  val miskBody : ResponseBody = object: ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      TODO(
          "not implemented") //To change body of created functions use File | Settings | File Templates.
    }

  }
  return misk.web.Response(okBody, this.headers(), this.code())
}