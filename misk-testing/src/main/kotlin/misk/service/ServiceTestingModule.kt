package misk.service

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.ServiceDependencyOverride
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

/**
 * [ServiceTestingModule] provides additional help for testing service, notably allowing existing
 * services to be bound with additional dependencies specifically required for testing. Typically
 * used when a [Service] requires an external service (e.g. zookeeper, etcd, vitess, etc)
 * that is being spun up within the test itself
 */
class ServiceTestingModule<T : Service> internal constructor(
  private val wrappedServiceClass: KClass<T>,
  private val extraDependencies: Set<Key<*>>
) : KAbstractModule() {

  override fun configure() {
    multibind<ServiceDependencyOverride>()
        .toInstance(ServiceDependencyOverride(wrappedServiceClass, extraDependencies))
  }

  companion object {
    /** @return A [Module] binding the service with extra dependencies */
    fun <T : Service> withExtraDependencies(
      wrappedServiceClass: KClass<T>,
      vararg extraDependencies: Key<*>
    ): com.google.inject.Module =
        ServiceTestingModule(wrappedServiceClass, extraDependencies.toSet())

    /** @return A [Module] binding the given service with extra dependencies */
    inline fun <reified T : Service> withExtraDependencies(
      vararg extraDependencies: Key<*>
    ): com.google.inject.Module = withExtraDependencies(T::class, *extraDependencies)
  }
}