package misk.scope

/**
 * An [ActionScopedProvider] is implemented by components and application code that wants
 * provide contextual information based on an incoming request, job data, etc.
 */
interface ActionScopedProvider<out T> {
  fun get(): T
}
