package misk.scope

import com.google.inject.Key
import javax.inject.Inject

internal class RealActionScoped<T> @Inject internal constructor(
  val key: Key<T>,
  val scope: ActionScope
) : ActionScoped<T> {
  override fun get(): T = scope.get(key)
}
