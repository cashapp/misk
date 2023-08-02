package wisp.tracing

import io.opentracing.Span

/**
 * A Tag is a name-value pair which will be added to a [Span].
 * Only primitive types are supported like [Boolean]s, [Number]s, and [String]s.
 */
data class Tag<T>(val name: String, val value: T) {
    init {
        check(value is Boolean || value is Number || value is String) {
            "Only primitive trace tag value types are allowed."
        }
    }
}

/**
 * Conveniently set tags all at once.
 */
fun Span.setTags(tags: Collection<Tag<*>>) = tags.forEach { setTag(it) }

fun Span.setTag(tag: Tag<*>) {
    when (tag.value) {
        is Boolean -> setTag(tag.name, tag.value)
        is Number -> setTag(tag.name, tag.value)
        is String -> setTag(tag.name, tag.value)
    }
}
