package com.squareup.cash.tracing.datadog

import datadog.trace.api.CorrelationIdentifier
import org.slf4j.MDC
import wisp.logging.getLogger

/**
 * A scope listener that updates the MDC with the trace and span reference anytime a new scope is
 * activated
 */
// Generic exceptions are caught here to avoid crashing the critical path when setting MDC fails.
@Suppress("TooGenericExceptionCaught")
internal object MDCScopeListener {
  private val log = getLogger<MDCScopeListener>()

  private const val MDC_TRACE_ID = "trace_id"
  private const val MDC_SPAN_ID = "span_id"
  private const val MDC_DD_TRACE_ID = "dd.trace_id"
  private const val MDC_DD_SPAN_ID = "dd.span_id"

  class Activated : Runnable {
    override fun run() {
      try {
        MDC.put(MDC_TRACE_ID, CorrelationIdentifier.getTraceId())
        MDC.put(MDC_SPAN_ID, CorrelationIdentifier.getSpanId())
        MDC.put(MDC_DD_TRACE_ID, CorrelationIdentifier.getTraceId())
        MDC.put(MDC_DD_SPAN_ID, CorrelationIdentifier.getSpanId())
      } catch (e: Exception) {
        log.debug("Exception setting log context context", e)
      }
    }
  }

  class Closed : Runnable {
    override fun run() {
      try {
        MDC.remove(MDC_TRACE_ID)
        MDC.remove(MDC_SPAN_ID)
        MDC.remove(MDC_DD_TRACE_ID)
        MDC.remove(MDC_DD_SPAN_ID)
      } catch (e: Exception) {
        log.debug("Exception removing log context context", e)
      }
    }
  }
}
