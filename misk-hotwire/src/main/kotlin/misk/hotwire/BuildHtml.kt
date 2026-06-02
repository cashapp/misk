package misk.hotwire

import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML
import misk.web.ResponseBody
import okio.BufferedSink

fun buildHtml(renderer: TagConsumer<*>.() -> Unit) = StringBuilder().apply { appendHTML().renderer() }.toString()

fun buildHtmlResponseBody(renderer: TagConsumer<*>.() -> Unit): ResponseBody =
  object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.writeUtf8(buildHtml(renderer))
    }
  }
