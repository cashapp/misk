package misk.tracing.backends.jaeger

import com.uber.jaeger.Span
import com.uber.jaeger.reporters.Reporter
import com.uber.jaeger.samplers.Sampler
import com.uber.jaeger.samplers.SamplingStatus
import io.opentracing.Tracer
import misk.tracing.traceWithSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC

internal class MDCScopeManagerTest {

  @Test fun nestsMDC() {
    val tracer = newTracer()
    tracer.traceWithSpan("my-sample") { span1 ->
      // Should set on entry to span
      val context1 = span1.context() as com.uber.jaeger.SpanContext
      assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isEqualTo(String.format("%x", context1.traceId))
      assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isEqualTo(String.format("%x", context1.spanId))
      assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isEqualTo("0")

      tracer.traceWithSpan("nested-span") { span2 ->
        val context2 = span2.context() as com.uber.jaeger.SpanContext
        assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isEqualTo(String.format("%x", context2.traceId))
        assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isEqualTo(String.format("%x", context2.spanId))
        assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isEqualTo(String.format("%x", context1.spanId))
      }

      // Should restore when done
      assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isEqualTo(String.format("%x", context1.traceId))
      assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isEqualTo(String.format("%x", context1.spanId))
      assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isEqualTo("0")
    }

    // Should clear when done with top level span
    assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isNull()
    assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isNull()
    assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isNull()
  }

  private fun newTracer(): Tracer {
    val reporter = object : Reporter {
      override fun report(span: Span) {}
      override fun close() {}
    }

    val sampler = object : Sampler {
      override fun sample(operation: String, id: Long) = SamplingStatus.of(true, mapOf())
      override fun close() {}
    }

    return com.uber.jaeger.Tracer.Builder("my_server", reporter, sampler)
        .withScopeManager(MDCScopeManager())
        .build()
  }
}