package misk.scope

import com.google.inject.Key
import jakarta.inject.Inject
import kotlin.reflect.full.createType

internal class RealActionScoped<T> @Inject internal constructor(
  val key: Key<T>,
  val scope: ActionScope
) : ActionScoped<T> {
  override fun get(): T = scope.get(key::class.createType())
}
