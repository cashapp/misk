package misk.tracing

import io.opentracing.Span
import io.opentracing.Tracer

import wisp.tracing.trace as wispTrace
import wisp.tracing.traceWithNewRootSpan as wispTraceWithNewRootSpan
import wisp.tracing.traceWithSpan as wispTraceWithSpan

@Deprecated(
  message = "Prefer wisp-tracing.",
  replaceWith = ReplaceWith(
    expression = "this.trace(spanName, tags, f)",
    imports = ["wisp.tracing.trace"]
  )
)
fun <T : Any?> Tracer.trace(spanName: String, tags: Map<String, String> = mapOf(), f: () -> T): T =
  wispTrace(spanName, tags, f)

@Deprecated(
  message = "Prefer wisp-tracing.",
  replaceWith = ReplaceWith(
    expression = "this.traceWithSpan(spanName, tags, f)",
    imports = ["wisp.tracing.traceWithSpan"]
  )
)
fun <T : Any?> Tracer.traceWithSpan(
  spanName: String,
  tags: Map<String, String> = mapOf(),
  f: (Span) -> T
): T = wispTraceWithSpan(spanName, tags, f)

@Deprecated(
  message = "Prefer wisp-tracing.",
  replaceWith = ReplaceWith(
    expression = "this.traceWithNewRootSpan(spanName, tags, false, f)",
    imports = ["wisp.tracing.traceWithNewRootSpan"]
  )
)
fun <T : Any?> Tracer.traceWithNewRootSpan(
  spanName: String,
  tags: Map<String, String> = mapOf(),
  f: (Span) -> T
): T = wispTraceWithNewRootSpan(spanName, tags, retainBaggage = false, f)
