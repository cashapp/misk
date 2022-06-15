package wisp.tracing

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
): T {
    return traceWithSpanInternal(spanName, tags, true, f)
}

/** [traceWithNewRootSpan] traces the given function, always starting a new root span */
fun <T : Any?> Tracer.traceWithNewRootSpan(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    f: (Span) -> T
): T {
    return traceWithSpanInternal(spanName, tags, false, f)
}

private fun <T : Any?> Tracer.traceWithSpanInternal(
    spanName: String,
    tags: Map<String, String> = mapOf(),
    asChild: Boolean,
    f: (Span) -> T
): T {
    var spanBuilder = buildSpan(spanName)
    tags.forEach { (k, v) -> spanBuilder.withTag(k, v) }

    if (!asChild) {
        spanBuilder = spanBuilder.ignoreActiveSpan()
    }

    val span = spanBuilder.start()
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
