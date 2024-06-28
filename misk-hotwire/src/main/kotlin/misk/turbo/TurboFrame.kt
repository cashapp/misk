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
class TurboFrame @JvmOverloads constructor(
  id: String,
  src: String? = null,
  lazy: Boolean = false,
  consumer: TagConsumer<*>
) : HTMLTag(
  tagName = "turbo-frame",
  consumer = consumer,
  inlineTag = true,
  emptyTag = false,
  initialAttributes = mapOf(
    "id" to id,
    "src" to src.orEmpty(),
    "loading" to (if (lazy) "lazy" else ""),
  ),
), HtmlInlineTag

fun TagConsumer<*>.turbo_frame(
  id: String,
  src: String? = null,
  lazy: Boolean = false,
  block: TurboFrame.() -> Unit = {}
) {
  TurboFrame(id, src, lazy, this).visit(block)
}
