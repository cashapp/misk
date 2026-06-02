package com.squareup.cash.tracing.datadog

import datadog.trace.api.CorrelationIdentifier
import misk.logging.getLogger
import org.slf4j.MDC

/**
 * A scope listener that updates the MDC with the trace and span reference anytime a new scope is activated or closed.
 */
class MDCScopeListener {
  companion object {
    val log = getLogger<MDCScopeListener>()
    const val MDC_TRACE_ID = "trace_id"
    const val MDC_SPAN_ID = "span_id"
    const val MDC_DD_TRACE_ID = "dd.trace_id"
    const val MDC_DD_SPAN_ID = "dd.span_id"
  }

  fun afterScopeActivated() {
    try {
      MDC.put(MDC_TRACE_ID, CorrelationIdentifier.getTraceId())
      MDC.put(MDC_SPAN_ID, CorrelationIdentifier.getSpanId())
      MDC.put(MDC_DD_TRACE_ID, CorrelationIdentifier.getTraceId())
      MDC.put(MDC_DD_SPAN_ID, CorrelationIdentifier.getSpanId())
    } catch (e: Exception) {
      log.debug("Exception setting log context context", e)
    }
  }

  fun afterScopeClosed() {
    if (true) {
      // Dont remove MDC trace_ids on scope closed due to bugs in
      // the balance of activated/closed calls.  The activated call is
      // correct on scope changes, so it will properly keep the traceId
      // up-to-date.
      // https://github.com/DataDog/dd-trace-java/issues/7215#issuecomment-2841316047
      return
    }

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
