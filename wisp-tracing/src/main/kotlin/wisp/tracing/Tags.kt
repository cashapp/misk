package wisp.tracing

import io.opentracing.Span
import io.opentracing.tag.AbstractTag
import io.opentracing.tag.BooleanTag
import io.opentracing.tag.StringTag

/**
 * A Tag is a name-value pair which will be added to a [Span].
 * Only primitive types are supported like [Boolean]s, [Number]s, and [String]s.
 */
data class Tag<T>(val name: String, val value: T)

/**
 * Conveniently set tags all at once.
 */
fun Span.setTags(tags: Collection<Tag<*>>) = tags.forEach{ setTag(it) }

fun Span.setTag(tag: Tag<*>) {
  when (tag.value) {
    is Boolean -> BooleanTag(tag.name).set(this, tag.value)
    is Number -> NumberTag(tag.name).set(this, tag.value)
    is String -> StringTag(tag.name).set(this, tag.value)
    else -> error("Unsupported tag type ($tag). Only primitives are allowed.")
  }
}

// NB: Open tracing provides IntTag, but spans accept tags with Number type values
// using the Span.setTag(key, value) API.
// This seems like a short-sight, so we provide NumberTag.

private class NumberTag(key: String): AbstractTag<Number>(key) {
  override fun set(span: Span, tagValue: Number) {
    span.setTag(key, tagValue)
  }
}
