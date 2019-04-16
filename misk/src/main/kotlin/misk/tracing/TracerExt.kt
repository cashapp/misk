package misk.tracing

import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags

/** [trace] traces the given function with the specific span name and optional tags */
fun <T : Any?> Tracer.trace(spanName: String, tags: Map<String, String> = mapOf(), f: () -> T): T =
    traceWithSpan(spanName, tags) { f() }

/** [trace] traces the given function, passing the span into the function */
fun <T : Any?> Tracer.traceWithSpan(
  spanName: String,
  tags: Map<String, String> = mapOf(),
  customizer: (Tracer.SpanBuilder) -> Unit = {},
  f: (Span) -> T
): T {
  val spanBuilder = buildSpan(spanName)
  customizer(spanBuilder)
  tags.forEach { k, v -> spanBuilder.withTag(k, v) }

  activeSpan()?.let { spanBuilder.asChildOf(it) }

  val scope = spanBuilder.startActive(true)
  return try {
    f(scope.span())
  } catch (th: Throwable) {
    Tags.ERROR.set(scope.span(), true)
    throw th
  } finally {
    scope.close()
  }
}

/**
 * Start a new span with an explicit parent span, useful when connecting a child spand to a parent
 * span that isn't the active span. For example when running parts of a trace in a separate thread.
 */
fun <T : Any?> Tracer.traceWithChildSpan(
  spanName: String,
  span: Span,
  tags: Map<String, String> = mapOf(),
  customizer: (Tracer.SpanBuilder) -> Unit = {},
  f: (Span) -> T
): T =
    traceWithSpan(spanName, tags, { sb ->
      sb.asChildOf(span)
      customizer(sb)
    }, f)
