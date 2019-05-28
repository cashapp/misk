package misk.web

import okhttp3.Headers
import okio.Buffer
import okio.BufferedSink
import okio.buffer
import okio.sink
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

/** Returns a [ResponseBody] that writes this out as UTF-8. */
fun String.toResponseBody(): ResponseBody {
  return object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.writeUtf8(this@toResponseBody)
    }
  }
}

fun Response<*>.readUtf8(): String {
  val buffer = Buffer()
  (body as ResponseBody).writeTo(buffer)
  return buffer.readUtf8()
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

fun HttpServletResponse.bufferedSink() = outputStream.sink().buffer()

fun okhttp3.Response.toMisk(): Response<ResponseBody> {
  val miskBody = if (body() is okhttp3.ResponseBody) { object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        body()!!.use {
          sink.writeAll(it.source())
        }
      }
    }
  } else "".toResponseBody()
  return Response(miskBody, headers(), code())
}