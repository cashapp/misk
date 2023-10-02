package misk.scope

import kotlin.reflect.KType

interface Scope : AutoCloseable {
  fun <T> get(key: KType): T

  fun inScope(): Boolean

  fun enter(seedData: Map<out KType, Any?>): Scope
}
