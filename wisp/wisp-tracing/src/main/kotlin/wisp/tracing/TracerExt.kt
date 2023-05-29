package wisp.tracing

import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags

/** [trace] traces the given function with the specific span name and optional tags */
fun <T : Any?> Tracer.trace(spanName: String, tags: Map<String, String> = mapOf(), f: () -> T): T =
    traceWithSpan(spanName, tags) { f() }

/** [traceWithSpan] traces the given function, passing the span into the function.
 *  If a span is already active, the new span is made a child of the existing. */
fun <T : Any?> Tracer.traceWithSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    f: (Span) -> T
): T = traceWithSpanInternal(spanName, tags, asChild = true, retainBaggage = false, f)

/** [traceWithNewRootSpan] traces the given function, always starting a new root span */
fun <T : Any?> Tracer.traceWithNewRootSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    retainBaggage: Boolean = false,
    f: (Span) -> T
): T = traceWithSpanInternal(spanName, tags, asChild = false, retainBaggage = retainBaggage, f)

private fun <T : Any?> Tracer.traceWithSpanInternal(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    asChild: Boolean,
    retainBaggage: Boolean = false,
    f: (Span) -> T
): T {
    val activeSpan: Span? = this.activeSpan()

    var spanBuilder = buildSpan(spanName)
    tags.forEach { (k, v) -> spanBuilder.withTag(k, v) }

    if (!asChild) {
        spanBuilder = spanBuilder.ignoreActiveSpan()
    }

    val span = spanBuilder.start()

    if (retainBaggage && !asChild) {
        val baggage = activeSpan?.context()?.baggageItems() ?: emptyList()
        for ((k, v) in baggage) {
            span.setBaggageItem(k, v)
        }
    }

    val scope = scopeManager().activate(span)
    return try {
        f(span)
    } catch (t: Throwable) {
        Tags.ERROR.set(span, true)
        throw t
    } finally {
        scope.close()
        span.finish()
    }
}

/**
 * Instruments a function [f] with a new scope.
 * This is helpful if you need to create a new [Scope] for an existing [Span],
 * for example, if you are switching threads (since Scopes are not thread-safe).
 *
 * ```kotlin
 * tracer.traceWithSpan("thread-switching-span") {
 *   ...
 *   thread {
 *     tracer.withNewScope(span) { ... }
 *   }
 * }
 * ```
 */
inline fun <T: Any?> Tracer.withNewScope(
    span: Span,
    crossinline f: () -> T
): T = scopeManager().activate(span).use { f() }
