package misk

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import kotlin.reflect.KClass
import misk.inject.KAbstractModule
import misk.inject.toKey

/**
 * Adds dependency edges for a service registered by a [ServiceModule] installed elsewhere, without registering the
 * service itself. This lets a module that doesn't own a service order it against services it does know about:
 * ```
 * install(ServiceDependencyModule<OutboxForwarderService>()
 *     .dependsOnIfRegistered<OutboxPublisherService>())
 * ```
 *
 * Edges added with [dependsOn] fail injector creation unless both [key] and the upstream service are registered. Edges
 * added with [dependsOnIfRegistered] are dropped when either service is not registered.
 */
class ServiceDependencyModule
private constructor(
  val key: Key<out Service>,
  val dependsOn: List<Key<out Service>>,
  val dependsOnIfRegistered: List<Key<out Service>>,
) : KAbstractModule() {

  constructor(key: Key<out Service>) : this(key, listOf(), listOf())

  override fun configure() {
    for (dependsOnKey in dependsOn) {
      multibind<DependencyEdge>().toInstance(DependencyEdge(dependent = key, dependsOn = dependsOnKey))
    }
    for (dependsOnKey in dependsOnIfRegistered) {
      multibind<DependencyEdge>()
        .toInstance(DependencyEdge(dependent = key, dependsOn = dependsOnKey, ifRegistered = true))
    }
  }

  fun dependsOn(upstream: Key<out Service>) = ServiceDependencyModule(key, dependsOn + upstream, dependsOnIfRegistered)

  fun dependsOn(upstream: List<Key<out Service>>) =
    ServiceDependencyModule(key, dependsOn + upstream, dependsOnIfRegistered)

  fun dependsOnIfRegistered(upstream: Key<out Service>) =
    ServiceDependencyModule(key, dependsOn, dependsOnIfRegistered + upstream)

  fun dependsOnIfRegistered(upstream: List<Key<out Service>>) =
    ServiceDependencyModule(key, dependsOn, dependsOnIfRegistered + upstream)

  @JvmOverloads
  inline fun <reified T : Service> dependsOn(qualifier: KClass<out Annotation>? = null) =
    dependsOn(T::class.toKey(qualifier))

  @JvmOverloads
  inline fun <reified T : Service> dependsOnIfRegistered(qualifier: KClass<out Annotation>? = null) =
    dependsOnIfRegistered(T::class.toKey(qualifier))
}

inline fun <reified T : Service> ServiceDependencyModule(qualifier: KClass<out Annotation>? = null) =
  ServiceDependencyModule(T::class.toKey(qualifier))

inline fun <reified T : Service, reified Q : Annotation> ServiceDependencyModule() =
  ServiceDependencyModule(T::class.toKey(Q::class))
