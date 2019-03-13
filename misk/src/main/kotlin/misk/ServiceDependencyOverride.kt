package misk

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import kotlin.reflect.KClass

data class ServiceDependencyOverride(
  val service: KClass<out Service>,
  val extraDependencies: Set<Key<*>>
)