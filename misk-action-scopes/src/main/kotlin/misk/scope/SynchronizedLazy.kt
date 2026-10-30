package misk.scope

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private object UNINITIALIZED_VALUE

/**
 * Computes an action-scoped value on first read. [providerFactory] is only invoked then, so keys that a scope never
 * reads cost nothing beyond this wrapper.
 */
internal class SynchronizedLazy(private val providerFactory: () -> ActionScopedProvider<*>) : Lazy<Any?> {
  @Volatile private var _value: Any? = UNINITIALIZED_VALUE

  private val lock = ReentrantLock()

  override val value: Any?
    get() {
      if (_value !== UNINITIALIZED_VALUE) {
        return _value
      }

      return lock.withLock {
        val existingValue = _value
        if (existingValue != UNINITIALIZED_VALUE) {
          existingValue
        } else {
          _value = providerFactory().get()
          _value
        }
      }
    }

  override fun isInitialized(): Boolean {
    return _value != UNINITIALIZED_VALUE
  }

  internal fun copy(): SynchronizedLazy = SynchronizedLazy(providerFactory)
}
