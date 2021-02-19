package misk.hibernate

import com.google.inject.Injector
import org.hibernate.boot.registry.StandardServiceInitiator
import org.hibernate.service.ServiceRegistry
import org.hibernate.service.spi.ServiceRegistryImplementor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes the Misk injector to Hibernate extensions like UserTypes. Use it with the `injector`
 * extension val on ServiceRegistry. For example:
 *
 * ```
 * val moshi = serviceRegistry.injector.getInstance(Moshi::class.java)
 * ```
 */
@Singleton
internal class HibernateInjectorAccess @Inject constructor() : org.hibernate.service.Service,
  StandardServiceInitiator<HibernateInjectorAccess> {
  @Inject lateinit var injector: Injector

  override fun getServiceInitiated() = HibernateInjectorAccess::class.java

  override fun initiateService(
    configurationValues: MutableMap<Any?, Any?>?,
    registry: ServiceRegistryImplementor?
  ) = this
}

internal val ServiceRegistry.injector: Injector
  get() = requireService(HibernateInjectorAccess::class.java).injector
