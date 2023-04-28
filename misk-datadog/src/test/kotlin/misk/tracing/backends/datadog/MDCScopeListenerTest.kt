package misk.tracing.backends.datadog

import com.squareup.cash.tracing.datadog.MDCScopeListener
import datadog.trace.api.GlobalTracer
import datadog.trace.api.Tracer
import datadog.trace.api.interceptor.TraceInterceptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class MDCScopeListenerTest {
  @Test
  fun propagatesCorrelationIdentifiersToMDC() {
    val listener = MDCScopeListener();

    GlobalTracer.forceRegister(object : Tracer {
      override fun getTraceId() = "trace-id"

      override fun getSpanId() = "span-id"

      override fun addTraceInterceptor(traceInterceptor: TraceInterceptor) = false
    })

    listener.afterScopeActivated()

    assertEquals(MDC.get(MDCScopeListener.MDC_TRACE_ID), "trace-id")
    assertEquals(MDC.get(MDCScopeListener.MDC_SPAN_ID), "span-id")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_TRACE_ID), "trace-id")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_SPAN_ID), "span-id")

    listener.afterScopeClosed()

    assertEquals(MDC.get(MDCScopeListener.MDC_TRACE_ID), null)
    assertEquals(MDC.get(MDCScopeListener.MDC_SPAN_ID), null)
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_TRACE_ID), null)
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_SPAN_ID), null)
  }


  @Test
  fun preservesMDCWhenNestedScopeIsClosed() {
    val listener = MDCScopeListener();

    var currentTraceId = "trace-id-1"
    var currentSpanId = "span-id-1"

    GlobalTracer.forceRegister(object : Tracer {
      override fun getTraceId() = currentTraceId

      override fun getSpanId() = currentSpanId

      override fun addTraceInterceptor(traceInterceptor: TraceInterceptor) = false
    })

    listener.afterScopeActivated()

    assertEquals(MDC.get(MDCScopeListener.MDC_TRACE_ID), "trace-id-1")
    assertEquals(MDC.get(MDCScopeListener.MDC_SPAN_ID), "span-id-1")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_TRACE_ID), "trace-id-1")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_SPAN_ID), "span-id-1")

    currentTraceId = "trace-id-2"
    currentSpanId = "span-id-2"

    listener.afterScopeActivated()

    assertEquals(MDC.get(MDCScopeListener.MDC_TRACE_ID), "trace-id-2")
    assertEquals(MDC.get(MDCScopeListener.MDC_SPAN_ID), "span-id-2")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_TRACE_ID), "trace-id-2")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_SPAN_ID), "span-id-2")

    listener.afterScopeClosed()

    assertEquals(MDC.get(MDCScopeListener.MDC_TRACE_ID), "trace-id-1")
    assertEquals(MDC.get(MDCScopeListener.MDC_SPAN_ID), "span-id-1")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_TRACE_ID), "trace-id-1")
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_SPAN_ID), "span-id-1")

    listener.afterScopeClosed()

    assertEquals(MDC.get(MDCScopeListener.MDC_TRACE_ID), null)
    assertEquals(MDC.get(MDCScopeListener.MDC_SPAN_ID), null)
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_TRACE_ID), null)
    assertEquals(MDC.get(MDCScopeListener.MDC_DD_SPAN_ID), null)
  }

  @Test
  fun removesNewMDCValuesWhenScopeIsClosed() {
    val listener = MDCScopeListener();

    GlobalTracer.forceRegister(object : Tracer {
      override fun getTraceId() = "trace-id"

      override fun getSpanId() = "span-id"

      override fun addTraceInterceptor(traceInterceptor: TraceInterceptor) = false
    })

    MDC.put("EXISTING_VALUE_1", "existing-value-1")
    MDC.put("EXISTING_VALUE_2", "existing-value-2")

    listener.afterScopeActivated()

    MDC.put("NEW_VALUE_1", "new-value-1")
    MDC.put("NEW_VALUE_2", "new-value-2")

    assertEquals(MDC.get("EXISTING_VALUE_1"), "existing-value-1")
    assertEquals(MDC.get("EXISTING_VALUE_2"), "existing-value-2")
    assertEquals(MDC.get("NEW_VALUE_1"), "new-value-1")
    assertEquals(MDC.get("NEW_VALUE_2"), "new-value-2")

    listener.afterScopeClosed()

    assertEquals(MDC.get("EXISTING_VALUE_1"), "existing-value-1")
    assertEquals(MDC.get("EXISTING_VALUE_2"), "existing-value-2")
    assertEquals(MDC.get("NEW_VALUE_1"), null)
    assertEquals(MDC.get("NEW_VALUE_2"), null)
  }
}
