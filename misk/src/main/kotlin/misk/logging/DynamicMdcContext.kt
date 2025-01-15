package misk.logging

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.MDC
import org.slf4j.MDC.clear
import org.slf4j.MDC.setContextMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private typealias MDCContext = Map<String, String>

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
/**
 * A CoroutineContext element that allows for dynamic MDC (Mapped Diagnostic Context) management
 *
 * Uses a [CopyableThreadContextElement] to make updates to the MDC coroutine safe. The thread
 * local MDC is copied whenever a child coroutine inherits a context containing it.
 *
 * see [CopyableThreadContextElement] for details
 *
 * @param mdcContext the initial MDC context to be used in the coroutine. If null,
 * the current MDC context will be copied.
 */
internal class DynamicMdcContext(
  @Suppress("MemberVisibilityCanBePrivate")
  val mdcContext: MDCContext? = MDC.getCopyOfContextMap()
) : CopyableThreadContextElement<MDCContext?>, AbstractCoroutineContextElement(Key) {

  companion object Key : CoroutineContext.Key<DynamicMdcContext>

  override fun updateThreadContext(context: CoroutineContext): MDCContext? {
    setCurrentMdc(mdcContext)
    return null
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: MDCContext?) {
    clear()
  }

  override fun copyForChild(): CopyableThreadContextElement<MDCContext?> {
    return DynamicMdcContext(getCurrentMdc())
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
    return DynamicMdcContext(getCurrentMdc())
  }

  private fun getCurrentMdc(): MDCContext? = MDC.getCopyOfContextMap()
  private fun setCurrentMdc(contextMap: MDCContext?) {
    contextMap?.let { setContextMap(it) } ?: clear()
  }

}
