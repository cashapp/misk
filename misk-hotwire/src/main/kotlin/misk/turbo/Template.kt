package misk.turbo

import kotlinx.html.HTMLTag
import kotlinx.html.HtmlInlineTag
import kotlinx.html.TagConsumer
import kotlinx.html.visit

/**
 * Produces <template /> HTML tag as required to define Hotwire Turbo using kotlinx.html.
 *
 * Follows the spec from Hotwire docs: https://turbo.hotwired.dev/handbook/streams
 */
class Template(consumer: TagConsumer<*>) :
  HTMLTag(
    tagName = "template",
    consumer = consumer,
    initialAttributes = emptyMap(),
    inlineTag = true,
    emptyTag = false,
  ),
  HtmlInlineTag

fun TagConsumer<*>.template(block: Template.() -> Unit = {}) {
  Template(this).visit(block)
}
