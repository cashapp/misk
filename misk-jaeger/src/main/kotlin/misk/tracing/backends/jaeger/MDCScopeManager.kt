package misk.tracing.backends.jaeger

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

  override fun active(): Scope? = threadLocalStorage.get()

  override fun activate(span: Span, finishSpanOnClose: Boolean): Scope {
    val parentScope = threadLocalStorage.get()
    val newScope = MDCScope(this, parentScope, span, finishSpanOnClose)
    threadLocalStorage.set(newScope)
    putToMDC(newScope.span())
    return newScope
  }

  private fun close(scope: MDCScope) {
    val parentScope = scope.parentScope
    if (parentScope == null) {
      allContextNames.forEach { MDC.remove(it) }
      threadLocalStorage.remove()
    } else {
      putToMDC(parentScope.span())
      threadLocalStorage.set(parentScope)
    }
  }

  private fun putToMDC(span: Span) {
    (span as? com.uber.jaeger.Span)?.let {
      MDC.put(MDC_TRACE_ID, String.format("%x", it.context().traceId))
      MDC.put(MDC_SPAN_ID, String.format("%x", it.context().spanId))
      MDC.put(MDC_PARENT_ID, String.format("%x", it.context().parentId))
    }
  }

  private class MDCScope(
    private val scopeManager: MDCScopeManager,
    internal val parentScope: MDCScope?,
    private val span: Span,
    private val finishSpanOnClose: Boolean
  ) : Scope {
    override fun span() = span

    override fun close() {
      scopeManager.close(this)
      if (finishSpanOnClose) span.finish()
    }
  }

  internal companion object {
    const val MDC_TRACE_ID = "trace_id"
    const val MDC_SPAN_ID = "span_id"
    const val MDC_PARENT_ID = "parent_id"

    val allContextNames = listOf(MDC_TRACE_ID, MDC_SPAN_ID, MDC_PARENT_ID)
  }
}
