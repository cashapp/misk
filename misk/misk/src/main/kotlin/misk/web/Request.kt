package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okio.BufferedSink
import okio.BufferedSource
import org.eclipse.jetty.http.HttpMethod

data class Request(
  val url: HttpUrl,
  val method: HttpMethod = HttpMethod.GET,
  val headers: Headers = Headers.of(),
  val body: BufferedSource,
  val websocket: WebSocket? = null
) {
  fun toOkHttp3(): okhttp3.Request {
    // TODO(adrw) https://github.com/square/misk/issues/279
    val okRequestBody = if (this.method == HttpMethod.GET) {
      null
    } else {
      object : RequestBody() {
        override fun contentType() = null
        override fun writeTo(sink: BufferedSink) {
          sink.writeAll(body)
        }
      }
    }

    return okhttp3.Request.Builder()
        .url(url)
        .method(method.toString(), okRequestBody)
        .headers(headers)
        .build()
  }
}
