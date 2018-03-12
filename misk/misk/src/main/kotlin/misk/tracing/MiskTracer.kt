package misk.tracing

import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import javax.inject.Inject

class MiskTracer @Inject internal constructor(
  private val tracer: Tracer
) {
  /**
   * Adds span/sub-span to trace for method provided as a parameter. Use this method to do ad-hoc
   * tracing on methods you're interested in.
   *
   * @param spanName name to be used for the span. Usually, should be a string representation
   * of the method you plan to trace
   */
  fun <R> trace(spanName: String, method: (span: Span) -> R) : R {
    val scope = tracer.buildSpan(spanName).startActive(true)
    return scope.use {
      try {
        method(scope.span())
      } catch (exception: Exception) {
        Tags.ERROR.set(scope.span(), true)
        throw exception
      }
    }
  }
}