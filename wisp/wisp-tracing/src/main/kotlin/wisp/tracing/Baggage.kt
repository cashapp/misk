package wisp.tracing

import io.opentracing.Span

/**
 * Conveniently sets baggage items all at once. Baggage values come from [Any.toString].
 */
@Deprecated("This module is deprecated as wisp.tracing is deprecated.")
fun Span.setBaggageItems(baggage: Map<String, Any>) {
    for ((key, value) in baggage) {
        setBaggageItem(key, value.toString())
    }
}
