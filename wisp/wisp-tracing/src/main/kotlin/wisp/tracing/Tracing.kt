package wisp.tracing

import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer

data class SpanAndScope(val span: Span, val scope: Scope)

/**
 * Traces a [block] using a new [Span] with [name], which is automatically finished when the
 * block completes execution.
 *
 * Use this method where you would otherwise use try-with-resources with the Java API.
 *
 * ```java
 * Span span = tracers.buildSpan("operation-name").start();
 * try(Scope scope = tracer.scopeManager().activate(span)) {
 *   ...
 * } finally {
 *   span.finish();
 * }
 * ```
 *
 * With wisp-tracing, simply do:
 *
 * ```kotlin
 * tracer.spanned("operation-name") { ... }
 * ```
 *
 * If you need to start a new span independent of the active span, set [ignoreActiveSpan] to true,
 * and optionally [retainBaggage].
 */
inline fun Tracer.spanned(
  name: String,
  ignoreActiveSpan: Boolean = false,
  retainBaggage: Boolean = false,
  crossinline block: SpanAndScope.() -> Unit
) {
  val activeSpan: Span? = this.activeSpan()
  val span = buildSpan(name)
    .apply { if (ignoreActiveSpan) ignoreActiveSpan() }
    .start()

  if (retainBaggage && ignoreActiveSpan) {
    val baggage = activeSpan?.context()?.baggageItems() ?: emptyList()
    for ((k, v) in baggage) {
      span.setBaggageItem(k, v)
    }
  }

  this.scopeManager().activate(span)

  try {
    this.scopeManager().activate(span).use { scope ->
      block(SpanAndScope(span, scope))
    }
  } finally {
    span.finish()
  }
}

/**
 * Instruments [block] with a new scope. Like [spanned], but uses the provided [span].
 * The span may optionally be finished after [block] completes.
 *
 * This is helpful if you need to create a new [Scope] for an existing [Span], for example, if you
 * are switching threads (since Scopes are not thread-safe).
 *
 * ```kotlin
 * tracer.scoped(span) {
 *   ...
 *   thread {
 *     tracer.scoped(span, finishSpan = false) { ... }
 *   }
 * }
 * ```
 */
inline fun Tracer.scoped(span: Span, finishSpan: Boolean = false, crossinline block: (Scope) -> Unit) {
  try {
    this.scopeManager().activate(span).use(block)
  } finally {
    if (finishSpan) {
      span.finish()
    }
  }
}

/**
 * Creates a span called [name] which is a child of [parent].
 */
fun Tracer.childSpan(name: String, parent: Span): Span =
  this.buildSpan(name).asChildOf(parent).start()

