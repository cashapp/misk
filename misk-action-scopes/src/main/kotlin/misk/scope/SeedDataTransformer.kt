package misk.scope

import kotlin.reflect.KType

/**
 * While executing, actions have [ActionScoped] values that can be injected by anything running
 * within the action.
 *
 * For example, these values are seeded by default for web actions:
 *
 *  * `HttpCall`
 *  * `HttpServletRequest`
 *
 * These types are the initial keys in the `seedData` map; they map to the corresponding instances.
 * Implementations of this interface may modify this map.
 *
 * To add new seed data keys, you must also subclass [ActionScopedProviderModule] and call
 * [ActionScopedProviderModule.bindSeedData]. This makes the seed data type injectable by Guice.
 */
interface SeedDataTransformer {
  fun transform(seedData: Map<KType, Any?>): Map<KType, Any?>
}
