package misk.turbo

import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML

fun buildHtml(renderer: TagConsumer<*>.() -> Unit) = StringBuilder().apply {
  appendHTML().renderer()
}.toString()

// TODO replace with ResponseBody
//fun buildHtml(renderer: TemplateRenderer): ResponseBody = object : ResponseBody {
//  override fun writeTo(sink: BufferedSink) {
//    sink.writeUtf8(appendHTML().renderer())
//  }
//}
