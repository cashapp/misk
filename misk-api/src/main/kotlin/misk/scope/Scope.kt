package misk.scope

import com.google.inject.Key

interface Scope : AutoCloseable {
  fun <T> get(key: Key<T>): T

  fun inScope(): Boolean

  fun enter(seedData: Map<Key<*>, Any?>): Scope
}
