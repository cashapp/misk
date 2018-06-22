package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.BufferedSource
import org.eclipse.jetty.http.HttpMethod
import java.io.IOException
import java.net.HttpURLConnection

data class Request(
  val url: HttpUrl,
  val method: HttpMethod = HttpMethod.GET,
  val headers: Headers = Headers.of(),
  val body: BufferedSource,
  val websocket: WebSocket? = null
)

fun okhttp3.Request.exec(): okhttp3.Response {
  val client = OkHttpClient()
  return try {
    client.newCall(this).execute()
  } catch (e: IOException) {
    Response.Builder()
        .request(this)
        .protocol(Protocol.HTTP_1_1)
        .addHeader("Content-Type", "text/plain; charset=utf-8")
        .body(ResponseBody.create(MediaType.parse("Failed to fetch upstream URL ${this.url()}"), "Failed to fetch upstream URL ${this.url()}"))
        .message("Failed to fetch upstream URL ${this.url()}")
        .code(HttpURLConnection.HTTP_UNAVAILABLE)
        .build()
  }
}

fun Request.forwardRequestTo(proxyUrl: HttpUrl): misk.web.Response<*> {
  return this.toOkHttp3().setUrl(proxyUrl).exec().toMisk()
}

fun okhttp3.Request.setUrl(newUrl: HttpUrl): okhttp3.Request {
  return okhttp3.Request.Builder()
      .url(newUrl)
      .method(this.method(), this.body())
      .headers(this.headers())
      .build()
}

fun Request.toOkHttp3(): okhttp3.Request {
  // @TODO(adrw) https://github.com/square/misk/issues/279
  val okRequestBody = if (this.method == HttpMethod.GET) null
  else {
    object : RequestBody() {
      override fun contentType(): MediaType? = null
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