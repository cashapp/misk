package misk

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.inject.ConditionalProvider
import misk.inject.KAbstractModule
import misk.inject.Switch
import misk.inject.asSingleton
import misk.inject.toKey
import kotlin.reflect.KClass

/**
 * # Misk Services
 *
 * Services in Misk can depend on other services.
 *
 * ### Dependencies
 *
 * Suppose we have a `DatabaseService` and a `MovieService`, with the `MovieService` depending on the `DatabaseService`.
 *
 * ```
 * DatabaseService
 *   depended on by MovieService
 * ```
 *
 * When you install a service via this module, start-up and shut-down of its dependencies are handled automatically, so
 * that a service can only run when the services it depends on are running. In the example above, the `MovieService`
 * doesn't enter the `STARTING` state until the `DatabaseService` has entered the `RUNNING` state. Conversely, the
 * `MovieService` must enter the `TERMINATED` state before the DatabaseService enters the `STOPPING` state.
 *
 * Dependencies can have their own dependencies, so there's an entire graph to manage of what starts and stops when.
 *
 * ### Enhancements
 *
 * Some services exist to enhance the behavior of another service.
 *
 * For example, a `DatabaseService` may manage a generic connection to a MySQL database, and the
 * `SchemaMigrationService` may create tables specific to the application.
 *
 * We treat such enhancements as implementation details of the enhanced service: they depend on the service, but
 * downstream dependencies like the `MovieService` don't need to know that they exist.
 *
 * ```
 * DatabaseService
 *   enhanced by SchemaMigrationService
 *   depended on by MovieService
 * ```
 *
 * In the above service graph we start the `DatabaseService` first, the `SchemaMigrationService` second, and finally the
 * `MovieService`. The `MovieService` doesn't need to express a dependency on the `SchemaMigrationService`, that happens
 * automatically for enhancements.
 *
 * ### What does this look like?
 *
 * Instead of using the regular service multi-bindings you might be used to, in the `configure` block of a Guice
 * [KAbstractModule], you would set up the above relationship as follows:
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
 * Bindings are hooked up for a [ServiceManager] provider, which decorates the service with its dependencies and
 * enhancements to defer its start up and shut down until its dependent services are ready.
 *
 * This service will stall in the `STARTING` state until all upstream services are `RUNNING`. Symmetrically it stalls in
 * the `STOPPING` state until all dependent services are `TERMINATED`.
 */
class ServiceModule
// TODO re-enable JVM overloads and remove the alternative constructors once downstream usages have been migrated
//@JvmOverloads
@Suppress("detekt:AnnotatePublicApisWithJvmOverloads")
constructor(
  val key: Key<out Service>,
  val dependsOn: List<Key<out Service>> = listOf(),
  val enhancedBy: List<Key<out Service>> = listOf(),
  private val switchKey: String = "default",
  private val switchType: KClass<out Switch>? = null,
) : KAbstractModule() {

  // This constructor exists for binary-compatibility with older callers.
  @Deprecated("the enhances argument does nothing please don't use this.")
  constructor(
    key: Key<out Service>,
    dependsOn: List<Key<out Service>> = listOf(),
    enhancedBy: List<Key<out Service>> = listOf(),
    @Suppress("UNUSED_PARAMETER") enhances: Key<out Service>? = null,
  ) : this(key = key, dependsOn = dependsOn, enhancedBy = enhancedBy, switchKey = "default")

  // This constructor exists for binary-compatibility with older callers.
  @Deprecated("Use the new constructor which includes support for switch and new conditionOn APIs.")
  constructor(
    key: Key<out Service>,
    dependsOn: List<Key<out Service>> = listOf(),
    enhancedBy: List<Key<out Service>> = listOf(),
  ) : this(key = key, dependsOn = dependsOn, enhancedBy = enhancedBy, switchKey = "default")

  override fun configure() {
    if (switchType != null) {
      // TODO combine optional and non after rolled out and tested
      multibind<OptionalServiceEntry>()
        .toProvider(
          ConditionalProvider(
            switchKey,
            switchType,
            OptionalServiceEntry::class,
            ServiceEntry::class,
            ServiceEntry(key),
            null,
          ) {
            OptionalServiceEntry(key, switchKey, switchType, it)
          }
        ).asSingleton()
    } else {
      // TODO remove this branch and rely solely on ConditionalProvider after further testing in all cases
      multibind<ServiceEntry>().toInstance(ServiceEntry(key))
    }

    for (dependsOnKey in dependsOn) {
      if (switchType != null) {
        multibind<OptionalDependencyEdge>()
          .toProvider(
            ConditionalProvider(
              switchKey,
              switchType,
              OptionalDependencyEdge::class,
              DependencyEdge::class,
              DependencyEdge(dependent = key, dependsOn = dependsOnKey),
              null,
            ) {
              OptionalDependencyEdge(key, switchKey, switchType, it)
            }
          ).asSingleton()
      } else {
        // TODO remove this branch and rely solely on ConditionalProvider after further testing in all cases
        multibind<DependencyEdge>().toInstance(DependencyEdge(dependent = key, dependsOn = dependsOnKey))
      }
    }
    for (enhancedByKey in enhancedBy) {
      if (switchType != null) {
        multibind<OptionalEnhancementEdge>()
          .toProvider(
            ConditionalProvider(
              switchKey,
              switchType,
              OptionalEnhancementEdge::class,
              EnhancementEdge::class,
              EnhancementEdge(toBeEnhanced = key, enhancement = enhancedByKey),
              null,
            ) {
              OptionalEnhancementEdge(key, switchKey, switchType, it)
            }
          ).asSingleton()
      } else {
        // TODO remove this branch and rely solely on ConditionalProvider after further testing in all cases
        multibind<EnhancementEdge>().toInstance(EnhancementEdge(toBeEnhanced = key, enhancement = enhancedByKey))
      }
    }
  }

  fun conditionalOn(switchKey: String, switchType: KClass<out Switch>) =
    ServiceModule(key, dependsOn, enhancedBy, switchKey, switchType)

  fun dependsOn(upstream: Key<out Service>) = ServiceModule(key, dependsOn + upstream, enhancedBy, switchKey, switchType)

  fun dependsOn(upstream: List<Key<out Service>>) = ServiceModule(key, dependsOn + upstream, enhancedBy, switchKey, switchType)

  fun enhancedBy(enhancement: Key<out Service>) = ServiceModule(key, dependsOn, enhancedBy + enhancement, switchKey, switchType)

  fun enhancedBy(enhancement: List<Key<out Service>>) = ServiceModule(key, dependsOn, enhancedBy + enhancement, switchKey, switchType)

  @JvmOverloads
  inline fun <reified T : Switch> conditionalOn(switchKey: String = "default") =
    conditionalOn(switchKey, T::class)

  @JvmOverloads
  inline fun <reified T : Service> dependsOn(qualifier: KClass<out Annotation>? = null) =
    dependsOn(T::class.toKey(qualifier))

  @JvmOverloads
  inline fun <reified T : Service> enhancedBy(qualifier: KClass<out Annotation>? = null) =
    enhancedBy(T::class.toKey(qualifier))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ServiceModule) return false
    if (key != other.key) return false
    if (dependsOn != other.dependsOn) return false
    if (enhancedBy != other.enhancedBy) return false
    if (switchKey != other.switchKey) return false
    if (switchType != other.switchType) return false
    return true
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + dependsOn.hashCode()
    result = 31 * result + enhancedBy.hashCode()
    result = 31 * result + switchKey.hashCode()
    result = 31 * result + (switchType?.hashCode() ?: 0)
    return result
  }
}

/**
 * Returns a [ServiceModule] and hooks up service dependencies and enhancements.
 *
 * Here's how:
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

inline fun <reified T : Service, reified Q : Annotation> ServiceModule(
) = ServiceModule(T::class.toKey(Q::class))

internal data class EnhancementEdge(val toBeEnhanced: Key<*>, val enhancement: Key<*>)

internal data class DependencyEdge(val dependent: Key<*>, val dependsOn: Key<*>)

internal data class ServiceEntry(val key: Key<out Service>)

// TODO combine optional and non after rolled out and tested
// Wrapper types to handle optional conditional contributions
// Each wrapper contains the service key, switchKey, and switchType to make entries unique per conditional registration
internal data class OptionalServiceEntry(
  val serviceKey: Key<out Service>,
  val switchKey: String,
  val switchType: KClass<out Switch>,
  val entry: ServiceEntry?
)
internal data class OptionalDependencyEdge(
  val serviceKey: Key<out Service>,
  val switchKey: String,
  val switchType: KClass<out Switch>,
  val edge: DependencyEdge?
)
internal data class OptionalEnhancementEdge(
  val serviceKey: Key<out Service>,
  val switchKey: String,
  val switchType: KClass<out Switch>,
  val edge: EnhancementEdge?
)