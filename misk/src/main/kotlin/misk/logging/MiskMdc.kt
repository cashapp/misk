package misk.logging

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.MDC

@Singleton
internal class MiskMdc @Inject constructor(): Mdc {

  override fun put(key: String, value: String?) {
    MDC.put(key, value)
  }

  override fun get(key: String): String? = MDC.get(key)
}
