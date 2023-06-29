package misk.turbo

import kotlinx.html.HTMLTag
import kotlinx.html.HtmlInlineTag
import kotlinx.html.TagConsumer
import kotlinx.html.visit

/**
 * Produces <turbo-frame /> HTML tag as required to define Hotwire Turbo using kotlinx.html.
 *
 * Follows the spec from Hotwire docs: https://turbo.hotwired.dev/handbook/frames
 */
class TurboFrame(id: String, consumer: TagConsumer<*>) : HTMLTag(
  tagName = "turbo-frame",
  consumer = consumer,
  initialAttributes = mapOf(
    "id" to id
  ),
  inlineTag = true,
  emptyTag = false
), HtmlInlineTag

fun TagConsumer<*>.turbo_frame(id: String, block: TurboFrame.() -> Unit = {}) {
  TurboFrame(id, this).visit(block)
}
