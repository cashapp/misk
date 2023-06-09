package misk.api.scope

interface ActionScope {
  fun <T> get(type: Class<T>): T?

  fun inScope(): Boolean
}
