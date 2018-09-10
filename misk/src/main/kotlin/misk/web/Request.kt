package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.BufferedSource

data class Request(
  val url: HttpUrl,
  val dispatchMechanism: DispatchMechanism = DispatchMechanism.GET,
  val headers: Headers = Headers.of(),
  val body: BufferedSource,
  val websocket: WebSocket? = null
) {
  fun toOkHttp3(): okhttp3.Request {
    // TODO(adrw) https://github.com/square/misk/issues/279
    val okRequestBody = when (dispatchMechanism) {
      DispatchMechanism.GET -> null
      DispatchMechanism.WEBSOCKET -> null
      else -> object : RequestBody() {
        override fun contentType(): MediaType? = null
        override fun writeTo(sink: BufferedSink) {
          sink.writeAll(body)
        }
      }
    }

    return okhttp3.Request.Builder()
        .url(url)
        .method(dispatchMechanism.method.toString(), okRequestBody)
        .headers(headers)
        .build()
  }
}
