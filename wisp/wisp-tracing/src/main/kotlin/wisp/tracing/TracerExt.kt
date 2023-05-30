package wisp.tracing

import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.tag.Tags

/**
 * Traces a function [f], using a span called [spanName], which is automatically finished when the
 * function completes execution.
 *
 * If a span is already active, the new span is made a child of the existing one.
 * If you want to manipulate the [Span] (e.g. to attach baggage), use [traceWithSpan] instead.
 *
 * If you want a new independent span, use [traceWithNewRootSpan].
 */
fun <T : Any?> Tracer.trace(spanName: String, tags: Map<String, String> = mapOf(), f: () -> T): T =
    traceWithSpan(spanName, tags) { f() }

/**
 * Like [trace], but exposes the new active [Span] to [f].
 */
fun <T : Any?> Tracer.traceWithSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    f: (Span) -> T
): T = traceWithSpanInternal(buildChildSpan(spanName, tags), f)

/**
 * Like [traceWithSpan], but always starts a new independent (root) span.
 * If you'd like to continue propagating baggage that was set on the previous active span, set [retainBaggage] to true.
 */
fun <T : Any?> Tracer.traceWithNewRootSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    retainBaggage: Boolean = false,
    f: (Span) -> T
): T = traceWithSpanInternal(buildRootSpan(spanName, tags, retainBaggage = retainBaggage), f)

private fun Tracer.buildChildSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
): Span = buildSpan(spanName).addAllTags(tags).start()

private fun Tracer.buildRootSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    retainBaggage: Boolean = false,
): Span {
    val activeSpan: Span? = this.activeSpan()

    var spanBuilder = buildSpan(spanName).addAllTags(tags)
    spanBuilder = spanBuilder.ignoreActiveSpan()
    val span = spanBuilder.start()

    if (retainBaggage) {
        val baggage = activeSpan?.context()?.baggageItems() ?: emptyList()
        for ((k, v) in baggage) {
            span.setBaggageItem(k, v)
        }
    }
    return span
}

private fun <T : Any?> Tracer.traceWithSpanInternal(span: Span, block: (Span) -> T): T {
    val scope = scopeManager().activate(span)
    return try {
        block(span)
    } catch (t: Throwable) {
        Tags.ERROR.set(span, true)
        throw t
    } finally {
        scope.close()
        span.finish()
    }
}

private fun SpanBuilder.addAllTags(tags: Map<String, String>): SpanBuilder {
    tags.forEach { (k, v) -> this.withTag(k, v) }
    return this
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
