package misk.service

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provider
import misk.DependentService
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import kotlin.reflect.KClass

/**
 * [ServiceTestingModule] provides additional help for testing service, notably allowing existing
 * services to be bound with additional dependencies specifically required for testing. Typically
 * used when a [Service] requires an external service (e.g. zookeeper, etcd, vitess, etc)
 * that is being spun up within the test itself
 */
class ServiceTestingModule<T : Service> internal constructor(
  private val wrappedServiceType: KClass<T>,
  private val extraDependencies: Set<Key<*>>
) : KAbstractModule() {

  override fun configure() {
    // Register a wrapper around the service with the service manager. The wrapper delegates
    // to the underlying service for all calls except [DependentService.consumedKeys], which
    // it overrides to extend the consumed service list with the extra dependencies
    multibind<Service>().toProvider(ServiceWithTestDependenciesProvider(
        getProvider(wrappedServiceType.java),
        extraDependencies
    )).asSingleton()
  }

  companion object {
    /** @return A [Module] binding the service with extra dependencies */
    fun <T : Service> withExtraDependencies(
      wrappedServiceType: KClass<T>,
      vararg extraDependencies: Key<*>
    ): com.google.inject.Module =
        ServiceTestingModule(wrappedServiceType, extraDependencies.toSet())

    /** @return A [Module] binding the given service with extra dependencies */
    inline fun <reified T : Service> withExtraDependencies(
      vararg extraDependencies: Key<*>
    ): com.google.inject.Module = withExtraDependencies(T::class, *extraDependencies)
  }

  /**
   * The [ServiceWithTestDependencies] wraps the given service and adds additional test specific
   * dependencies
   */
  private class ServiceWithTestDependencies internal constructor(
    private val delegate: Service,
    extraDependencies: Set<Key<*>>
  ) : Service by delegate, DependentService {

    private val delegateDependentService: DependentService? = delegate as? DependentService
    private val baseConsumedKeys: Set<Key<*>> = delegateDependentService?.consumedKeys ?: setOf()

    override val consumedKeys: Set<Key<*>> = baseConsumedKeys + extraDependencies
    override val producedKeys: Set<Key<*>> = delegateDependentService?.producedKeys ?: setOf()
  }

  private class ServiceWithTestDependenciesProvider(
    private val wrappedServiceProvider: Provider<out Service>,
    private val extraDependencies: Set<Key<*>>
  ) : Provider<ServiceWithTestDependencies> {
    override fun get(): ServiceWithTestDependencies {
      return ServiceWithTestDependencies(wrappedServiceProvider.get(), extraDependencies)
    }
  }
}