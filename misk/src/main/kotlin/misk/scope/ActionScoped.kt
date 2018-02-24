package misk.scope

/** Provides access to a context object specific to the current action */
interface ActionScoped<out T> {
  fun get(): T

  companion object {
    /** @return an [ActionScoped] hard-coded to a specific value, useful for tests */
    fun <T> of(value: T) = object : ActionScoped<T> {
      override fun get() = value
    }
  }
}
