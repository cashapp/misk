package misk.scope

import com.google.inject.Key
import jakarta.inject.Inject

internal class RealActionScoped<T> @Inject internal @JvmOverloads constructor(
  val key: Key<T>,
  val scope: ActionScope,
  val newArg: String? = null
) : ActionScoped<T> {
  override fun get(): T = scope.get(key)
}
