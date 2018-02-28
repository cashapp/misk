package misk.tracing

import io.opentracing.Tracer
import javax.inject.Inject

class MiskTracer @Inject internal constructor(
  private val tracer: Tracer
) {
  /**
   * Adds span/sub-span to trace for method provided as a parameter. Use this method to do ad-hoc
   * tracing on methods you're interested in.
   *
   * @param operationName name to be used for the span. Usually, should be a string representation
   * of the method you plan to trace
   */
  fun trace(operationName: String, method: () -> Any?) {
    tracer.buildSpan(operationName).startActive(true).use { method() }
  }
}