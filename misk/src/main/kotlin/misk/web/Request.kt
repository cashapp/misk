package misk.web

import misk.web.actions.WebSocket
import okhttp3.Headers
import okhttp3.HttpUrl
import okio.BufferedSource
import org.eclipse.jetty.http.HttpMethod

data class Request(
  val url: HttpUrl,
  val method: HttpMethod,
  val headers: Headers = Headers.of(),
  val body: BufferedSource,
  var websocket: WebSocket<Any>? = null
)
