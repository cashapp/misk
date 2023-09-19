package misk.scope

interface Scope : AutoCloseable {
  fun <T> get(type: Class<T>): T?

  fun inScope(): Boolean

  fun enter(seedData: Map<out Any, Any?>): Scope
}
