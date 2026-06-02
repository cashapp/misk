package misk.scope

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private object UNINITIALIZED_VALUE

internal class SynchronizedLazy(private val provider: ActionScopedProvider<*>) : Lazy<Any?> {
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
          _value = provider.get()
          _value
        }
      }
    }

  override fun isInitialized(): Boolean {
    return _value != UNINITIALIZED_VALUE
  }
}
