package misk.hotwire

import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML

fun buildHtml(renderer: TagConsumer<*>.() -> Unit) = StringBuilder().apply {
  appendHTML().renderer()
}.toString()

// TODO consider streaming response
//fun buildHtmlResponseBody(renderer: TagConsumer<*>.() -> Unit): ResponseBody = object : ResponseBody {
//  override fun writeTo(sink: BufferedSink) {
//    sink.writeUtf8(buildHtml { renderer() })
//  }
//}
