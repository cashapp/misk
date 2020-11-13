package misk.web

import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okio.BufferedSink

/** An HTTP response body, headers, and status code. */
data class Response<out T>(
  val body: T,
  val headers: Headers = headersOf(),
  val statusCode: Int = 200
)

interface ResponseBody {
  fun writeTo(sink: BufferedSink)
}
