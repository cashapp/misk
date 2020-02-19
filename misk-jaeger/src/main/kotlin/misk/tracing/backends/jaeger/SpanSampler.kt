package misk.tracing.backends.jaeger

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.samplers.ProbabilisticSampler
import io.opentracing.Span
import io.opentracing.Tracer
import misk.sampling.Sampler

/**
 * A sampler that deterministically samples based on the current tracing span ID.
 * Sampling rate when no span is present is 1.0.
 *
 * @param samplingRate Sampling rate. 0 is never and 1 is always.
 */
class SpanSampler(
  private val tracer: Tracer,
  samplingRate: Double
) : Sampler {

  init {
    check(samplingRate in 0.0..1.0) { "sampling rate must be in range [0, 1]" }
  }

  private val sampler = ProbabilisticSampler(samplingRate)

  /** Returns true if the sampling threshold is met for the current Jaeger span or there is no current span */
  override fun sample(): Boolean {
    val spanId: Long? = when (val span: Span? = tracer.activeSpan()) {
      is JaegerSpan -> span.context().spanId
      else -> null
    }

    return spanId == null || sampler.sample("irrelevant", spanId).isSampled
  }
}
