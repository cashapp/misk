package wisp.tracing

import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer

/**
 * Scopes a [block] to a [span], which is automatically finished when the block completes execution.
 * It is an error to use a finished [Span], which can lead to undefined behaviour. See [Span.finish].
 *
 * Use this method where you would otherwise use try-with-resources with the Java API.
 *
 * ```java
 * try(Scope scope = tracer.scopeManager().activate(span)) {
 *   ...
 * } finally {
 *   span.finish()
 * }
 * ```
 *
 * Because kotlin lacks try-with-resources, the equivalent kotlin would be:
 *
 * ```kotlin
 * try {
 *   tracer.scopeManager().activate(span).use { ... }
 * } finally {
 *   span.finish()
 * }
 *
 * With wisp-tracing, simply do:
 *
 * ```kotlin
 * tracer.scoped(span) { ... }
 * ```
 */
inline fun Tracer.scoped(span: Span, crossinline block: (Scope) -> Unit) {
  try {
    this.scopeManager().activate(span).use(block)
  } finally {
    span.finish()
  }
}

/**
 * Instruments [block] with a new scope. Like [scoped], but does not finish or otherwise manipulate
 * the [span].
 *
 * This is helpful if you need to create a new [Scope] for an existing [Span], for example, if you
 * are switching threads (since Scopes are not thread-safe).
 *
 * ```kotlin
 * tracer.scoped(span) {
 *   ...
 *   thread {
 *     tracer.newScope(span) { ... }
 *   }
 * }
 * ```
 */
inline fun Tracer.newScope(span: Span, crossinline block: (Scope) -> Unit) {
  this.scopeManager().activate(span).use(block)
}

/**
 * More conveniently set baggage items all at once.
 */
fun Span.setBaggageItems(baggage: Map<String, Any>) {
  for ((key, value) in baggage) {
    setBaggageItem(key, value.toString())
  }
}
