package misk.tracing

import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags

/** [trace] traces the given function with the specific span name */
fun <T : Any?> Tracer.trace(spanName: String, f: () -> T): T = traceWithSpan(spanName) { _ -> f() }

/** [trace] traces the given function, passing the span into the function */
fun <T : Any?> Tracer.traceWithSpan(spanName: String, f: (Span) -> T): T {
  val spanBuilder = buildSpan(spanName)
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
