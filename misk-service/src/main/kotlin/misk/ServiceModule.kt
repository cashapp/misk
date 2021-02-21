package misk

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.inject.KAbstractModule
import misk.inject.toKey
import kotlin.reflect.KClass

/**
 * # Misk Services
 *
 * Services in Misk can depend on other services.
 *
 * ### Dependencies
 *
 * Suppose we have a `DatabaseService` and a `MovieService`, with the `MovieService` depending on
 * the `DatabaseService`.
 *
 * ```
 * DatabaseService
 *   depended on by MovieService
 * ```
 *
 * When you install a service via this module, start-up and shut-down of its dependencies are
 * handled automatically, so that a service can only run when the services it depends on are
 * running. In the example above, the `MovieService` doesn't enter the `STARTING` state until the
 * `DatabaseService` has entered the `RUNNING` state. Conversely, the `MovieService` must enter the
 * `TERMINATED` state before the DatabaseService enters the `STOPPING` state.
 *
 * Dependencies can have their own dependencies, so there's an entire graph to manage of what starts
 * and stops when.
 *
 * ### Enhancements
 *
 * Some services exist to enhance the behavior of another service.
 *
 * For example, a `DatabaseService` may manage a generic connection to a MySQL database, and the
 * `SchemaMigrationService` may create tables specific to the application.
 *
 * We treat such enhancements as implementation details of the enhanced service: they depend on the
 * service, but downstream dependencies like the `MovieService` don't need to know that they exist.
 *
 * ```
 * DatabaseService
 *   enhanced by SchemaMigrationService
 *   depended on by MovieService
 * ```
 *
 * In the above service graph we start the `DatabaseService` first, the `SchemaMigrationService`
 * second, and finally the `MovieService`. The `MovieService` doesn't need to express a dependency
 * on the `SchemaMigrationService`, that happens automatically for enhancements.
 *
 * ### What does this look like?
 *
 * Instead of using the regular service multi-bindings you might be used to, in the `configure`
 * block of a Guice [KAbstractModule], you would set up the above relationship as follows:
 *
 * ```
 * override fun configure() {
 *   install(ServiceModule<SchemaMigrationService())
 *   install(ServiceModule<DatabaseService>()
 *       .enhancedBy<SchemaMigrationService>())
 *   install(ServiceModule<MoviesService>()
 *       .dependsOn<DatabaseService>())
 * }
 * ```
 *
 * ### How does this work?
 *
 * Bindings are hooked up for a [ServiceManager] provider, which decorates the service with its
 * dependencies and enhancements to defer its start up and shut down until its dependent services
 * are ready.
 *
 * This service will stall in the `STARTING` state until all upstream services are `RUNNING`.
 * Symmetrically it stalls in the `STOPPING` state until all dependent services are `TERMINATED`.
 */
class ServiceModule(
  val key: Key<out Service>,
  val dependsOn: List<Key<out Service>> = listOf(),
  val enhancedBy: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    multibind<ServiceEntry>().toInstance(ServiceEntry(key))

    for (dependsOnKey in dependsOn) {
      multibind<DependencyEdge>().toInstance(
        DependencyEdge(dependent = key, dependsOn = dependsOnKey)
      )
    }
    for (enhancedByKey in enhancedBy) {
      multibind<EnhancementEdge>().toInstance(
        EnhancementEdge(toBeEnhanced = key, enhancement = enhancedByKey)
      )
    }
  }

  fun dependsOn(upstream: Key<out Service>) = ServiceModule(
    key, dependsOn + upstream, enhancedBy
  )

  fun enhancedBy(enhancement: Key<out Service>) =
    ServiceModule(key, dependsOn, enhancedBy + enhancement)

  inline fun <reified T : Service> dependsOn(qualifier: KClass<out Annotation>? = null) =
    dependsOn(T::class.toKey(qualifier))

  inline fun <reified T : Service> enhancedBy(qualifier: KClass<out Annotation>? = null) =
    enhancedBy(T::class.toKey(qualifier))
}

/**
 * Returns a [ServiceModule] and hooks up service dependencies and enhancements.
 *
 * Here's how:
 *
 * ```
 * Guice.createInjector(object : KAbstractModule() {
 *   override fun configure() {
 *     install(ServiceModule<MyService>()
 *         .dependsOn<MyServiceDependency>())
 *     install(ServiceModule<MyServiceDependency>())
 *   }
 * }
 * ```
 *
 * Dependencies and services may be optionally annotated:
 *
 * ```
 * Guice.createInjector(object : KAbstractModule() {
 *   override fun configure() {
 *     install(ServiceModule<MyService>(MyAnnotation::class)
 *         .dependsOn<MyServiceDependency>(AnotherAnnotation::class))
 *     install(ServiceModule<MyServiceDependency>(AnotherAnnotation::class))
 *   }
 * }
 * ```
 */
inline fun <reified T : Service> ServiceModule(qualifier: KClass<out Annotation>? = null) =
  ServiceModule(T::class.toKey(qualifier))

internal data class EnhancementEdge(val toBeEnhanced: Key<*>, val enhancement: Key<*>)
internal data class DependencyEdge(val dependent: Key<*>, val dependsOn: Key<*>)
internal data class ServiceEntry(val key: Key<out Service>)
