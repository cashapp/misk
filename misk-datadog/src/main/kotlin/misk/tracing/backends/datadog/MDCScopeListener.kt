package com.squareup.cash.tracing.datadog

import datadog.trace.api.CorrelationIdentifier
import org.slf4j.MDC
import wisp.logging.getLogger

/**
 * A scope listener that updates the MDC with the trace and span reference anytime a new scope is
 * activated or closed.
 */
class MDCScopeListener {
  companion object {
    val log = getLogger<MDCScopeListener>()
    const val MDC_TRACE_ID = "trace_id"
    const val MDC_SPAN_ID = "span_id"
    const val MDC_DD_TRACE_ID = "dd.trace_id"
    const val MDC_DD_SPAN_ID = "dd.span_id"
  }

  private val contextStack = ArrayDeque<Map<String, String>>()

  fun afterScopeActivated() {
    try {
      captureCurrentMDC()

      val traceId = CorrelationIdentifier.getTraceId()
      val spanId = CorrelationIdentifier.getSpanId()

      MDC.put(MDC_TRACE_ID, traceId)
      MDC.put(MDC_SPAN_ID, spanId)
      MDC.put(MDC_DD_TRACE_ID, traceId)
      MDC.put(MDC_DD_SPAN_ID, spanId)
    } catch (e: Exception) {
      log.debug("Exception setting log context context", e)
    }
  }

  fun afterScopeClosed() {
    try {
      restorePreviousMDC()
    } catch (e: Exception) {
      log.debug("Exception removing log context context", e)
    }
  }

  private fun captureCurrentMDC() {
    val currentContext = MDC.getCopyOfContextMap() ?: emptyMap()
    contextStack.addLast(currentContext)
  }

  private fun restorePreviousMDC() {
    val previousContext = contextStack.removeLast()
    MDC.setContextMap(previousContext)
  }
}
