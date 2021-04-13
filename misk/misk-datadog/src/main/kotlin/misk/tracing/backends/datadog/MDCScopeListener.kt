package misk.tracing.backends.datadog

import datadog.trace.api.CorrelationIdentifier
import datadog.trace.context.ScopeListener
import org.slf4j.MDC
import wisp.logging.getLogger

/**
 * A scope listener that updates the MDC with the trace and span reference anytime a new scope is
 * activated or closed.
 */
class MDCScopeListener : ScopeListener {
  companion object {
    val log = getLogger<MDCScopeListener>()
    const val MDC_TRACE_ID = "trace_id"
    const val MDC_SPAN_ID = "span_id"
  }

  override fun afterScopeActivated() {
    try {
      MDC.put(MDC_TRACE_ID, CorrelationIdentifier.getTraceId())
      MDC.put(MDC_SPAN_ID, CorrelationIdentifier.getSpanId())
    } catch (e: Exception) {
      log.debug("Exception setting log context context", e)
    }
  }

  override fun afterScopeClosed() {
    try {
      MDC.remove(MDC_TRACE_ID)
      MDC.remove(MDC_SPAN_ID)
    } catch (e: Exception) {
      log.debug("Exception removing log context context", e)
    }
  }
}
