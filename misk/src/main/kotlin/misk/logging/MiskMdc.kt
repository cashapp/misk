package misk.logging

import org.slf4j.MDC

object MiskMdc : Mdc {

  override fun put(key: String, value: String?) {
    MDC.put(key, value)
  }

  override fun get(key: String): String? = MDC.get(key)

  override fun clear() {
    MDC.clear()
  }

  override fun setContextMap(context: MdcContextMap) {
    if (context.isNotEmpty()) {
      MDC.setContextMap(context)
    } else {
      MDC.clear()
    }
  }

  override fun getCopyOfContextMap(): MdcContextMap? = MDC.getCopyOfContextMap()
}
