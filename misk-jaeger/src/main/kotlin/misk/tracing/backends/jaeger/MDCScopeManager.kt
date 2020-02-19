package misk.tracing.backends.jaeger

import io.jaegertracing.internal.JaegerSpan
import io.opentracing.Scope
import io.opentracing.ScopeManager
import io.opentracing.Span
import org.slf4j.MDC

/**
 * [MDCScopeManager] is a [ScopeManager] that records the current trace-id, span-id, parent-id
 * in the current logging [MDC]. It is jaeger specific because getting access to these values
 * is dependent on the specific tracing vendor implementation.
 */
internal class MDCScopeManager : ScopeManager {
  private val threadLocalStorage = ThreadLocal<MDCScope>()

  override fun activate(span: Span): Scope {
    val parentScope = threadLocalStorage.get()
    val newScope = MDCScope(this, parentScope, span)
    threadLocalStorage.set(newScope)
    putToMDC(newScope)
    return newScope
  }

  override fun activeSpan(): Span? {
    return threadLocalStorage.get()?.span
  }

  private fun close(scope: MDCScope) {
    val parentScope = scope.parentScope
    if (parentScope == null) {
      allContextNames.forEach { MDC.remove(it) }
      threadLocalStorage.remove()
    } else {
      putToMDC(parentScope)
      threadLocalStorage.set(parentScope)
    }
  }

  private fun putToMDC(mdcScope: MDCScope) {
    val span = mdcScope.span as? JaegerSpan ?: return
    MDC.put(MDC_TRACE_ID, span.context().traceId)
    MDC.put(MDC_SPAN_ID, String.format("%x", span.context().spanId))
    MDC.put(MDC_PARENT_ID, String.format("%x", span.context().parentId))
  }

  private class MDCScope(
    private val scopeManager: MDCScopeManager,
    internal val parentScope: MDCScope?,
    internal val span: Span
  ) : Scope {
    override fun close() {
      scopeManager.close(this)
    }
  }

  internal companion object {
    const val MDC_TRACE_ID = "trace_id"
    const val MDC_SPAN_ID = "span_id"
    const val MDC_PARENT_ID = "parent_id"

    val allContextNames = listOf(MDC_TRACE_ID, MDC_SPAN_ID, MDC_PARENT_ID)
  }
}
