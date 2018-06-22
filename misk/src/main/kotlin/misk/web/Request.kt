package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.BufferedSource
import org.eclipse.jetty.http.HttpMethod
import java.net.ConnectException
import java.net.HttpURLConnection

data class Request(
  val url: HttpUrl,
  val method: HttpMethod = HttpMethod.GET,
  val headers: Headers = Headers.of(),
  val body: BufferedSource,
  val websocket: WebSocket? = null
)

fun Request.toOkHttp3(newUrl: HttpUrl?): Response {
  val client = OkHttpClient()

  val okUrl = if (newUrl != null) newUrl else this.url
  val okRequestBody = if (this.method == HttpMethod.GET) null else object : okhttp3.RequestBody() {
    override fun contentType(): MediaType? = null
    override fun writeTo(sink: BufferedSink) {
      sink.writeAll(body)
    }
  }

  val okRequest = okhttp3.Request.Builder()
      .url(okUrl)
      .method(this.method.asString(), okRequestBody)
      .headers(this.headers)
      .build()

  try {
    return client.newCall(okRequest).execute()
  } catch (e: ConnectException) {
    return Response.Builder()
        .addHeader("Content-Type", "text/plain; charset=utf-8")
        .code(HttpURLConnection.HTTP_UNAVAILABLE)
        .body(ResponseBody.create(MediaType.parse("Failed to fetch upstream URL $url"), "Failed to fetch upstream URL $url"))
        .build()
  }
}