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
  f: (Span) -> T
): T {
  val spanBuilder = buildSpan(spanName)
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
