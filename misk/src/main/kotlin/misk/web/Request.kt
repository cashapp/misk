package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.BufferedSink
import okio.BufferedSource
import org.eclipse.jetty.http.HttpMethod

data class Request(
  val url: HttpUrl,
  val method: HttpMethod = HttpMethod.GET,
  val headers: Headers = Headers.of(),
  val body: BufferedSource,
  val websocket: WebSocket? = null
)

fun Request.toOkHttp3(): Response {
  val client = OkHttpClient()

  val okRequestBody = object : okhttp3.RequestBody() {
    override fun contentType(): MediaType? = null
    override fun writeTo(sink: BufferedSink) {
      sink.writeAll(body)
    }
  }

  val okRequest = okhttp3.Request.Builder()
      .url(this.url)
      .method(this.method.asString(), okRequestBody)
      .headers(this.headers)
      .build()

  return client.newCall(okRequest).execute()
}