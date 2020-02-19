package misk.tracing.backends.jaeger

import io.jaegertracing.internal.JaegerTracer
import io.opentracing.SpanContext
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
      val context1 = span1.context() as SpanContext
      assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isEqualTo(context1.toTraceId())
      assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isEqualTo(context1.toSpanId())
      assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isEqualTo("0")

      tracer.traceWithSpan("nested-span") { span2 ->
        val context2 = span2.context() as SpanContext
        assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isEqualTo(context2.toTraceId())
        assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isEqualTo(context2.toSpanId())
        assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isEqualTo(context1.toSpanId())
      }

      // Should restore when done
      assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isEqualTo(context1.toTraceId())
      assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isEqualTo(context1.toSpanId())
      assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isEqualTo("0")
    }

    // Should clear when done with top level span
    assertThat(MDC.get(MDCScopeManager.MDC_TRACE_ID)).isNull()
    assertThat(MDC.get(MDCScopeManager.MDC_SPAN_ID)).isNull()
    assertThat(MDC.get(MDCScopeManager.MDC_PARENT_ID)).isNull()
  }

  private fun newTracer(): Tracer {
    return JaegerTracer.Builder("my_server")
        .withScopeManager(MDCScopeManager())
        .build()
  }
}
