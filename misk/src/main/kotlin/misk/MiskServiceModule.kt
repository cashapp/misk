package misk

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Binding
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.multibindings.MapBinderBinding
import com.google.inject.multibindings.MultibinderBinding
import com.google.inject.multibindings.MultibindingsTargetVisitor
import com.google.inject.multibindings.OptionalBinderBinding
import com.google.inject.spi.ConstructorBinding
import com.google.inject.spi.ConvertedConstantBinding
import com.google.inject.spi.DefaultBindingTargetVisitor
import com.google.inject.spi.InstanceBinding
import com.google.inject.spi.LinkedKeyBinding
import com.google.inject.spi.ProviderInstanceBinding
import com.google.inject.util.Types
import misk.concurrent.SleeperModule
import misk.environment.RealEnvVarModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.metrics.MetricsModule
import misk.moshi.MoshiModule
import misk.prometheus.PrometheusHistogramRegistryModule
import misk.resources.ResourceLoaderModule
import misk.time.ClockModule
import misk.time.TickerModule
import misk.tokens.TokenGeneratorModule
import mu.KotlinLogging
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Install this module in real environments.
 *
 * The vast majority of Service bindings belong in [MiskCommonServiceModule], in order to share
 * with [MiskTestingServiceModule]. Only bindings that are not suitable for a unit testing
 * environment belong here.
 */
class MiskRealServiceModule : KAbstractModule() {
  override fun configure() {
    install(ResourceLoaderModule())
    install(RealEnvVarModule())
    install(ClockModule())
    install(SleeperModule())
    install(TickerModule())
    install(MiskCommonServiceModule())
  }
}

/**
 * This module has common bindings for all environments (both real and testing).
 */
class MiskCommonServiceModule : KAbstractModule() {
  override fun configure() {
    binder().disableCircularProxies()
    binder().requireExactBindingAnnotations()
    install(MetricsModule())
    install(MoshiModule())
    install(TokenGeneratorModule())
    install(PrometheusHistogramRegistryModule())

    // Initialize empty sets for our multibindings.
    newMultibinder<HealthCheck>()
    newMultibinder<Service>()
    newMultibinder<ServiceManager.Listener>()

    multibind<ServiceManager.Listener>().toProvider(Provider<ServiceManager.Listener> {
      object : ServiceManager.Listener() {
        override fun failure(service: Service) {
          log.error(service.failureCause()) { "Service $service failed" }
        }
      }
    }).asSingleton()
    newMultibinder<ServiceEntry>()
    newMultibinder<DependencyEdge>()
    newMultibinder<EnhancementEdge>()
  }

  @Provides
  @Singleton
  internal fun provideServiceManager(
    injector: Injector,
    services: List<Service>,
    listeners: List<ServiceManager.Listener>,
    serviceEntries: List<ServiceEntry>,
    dependencies: List<DependencyEdge>,
    enhancements: List<EnhancementEdge>
  ): ServiceManager {
    // NB(mmihic): We get the binding for the Set<Service> because this uses a multibinder,
    // which allows us to retrieve the bindings for the elements
    val serviceListBinding = injector.getBinding(serviceSetKey)
    val invalidServices = serviceListBinding
        .acceptTargetVisitor(CheckServicesVisitor())
        .sorted()

    // Confirm all services have been registered as singleton. If they aren't singletons,
    // _readiness checks will fail
    check(invalidServices.isEmpty()) {
      "the following services are not marked as @Singleton: ${invalidServices.joinToString(", ")}"
    }

    val builder = ServiceGraphBuilder()

    // Support the deprecated DependantService interface.
    for (service in services) {
      var key : Key<*>
      when (service) {
        is DependentService -> {
          key = service.producedKeys.firstOrNull() ?: service::class.toKey()
          for (consumedKey in service.consumedKeys) {
            builder.addDependency(service = consumedKey, dependency = key)
          }
        }
        else -> {
          key = service::class.toKey()
        }
      }
      builder.addService(key, service)
    }

    // Support the new ServiceModule API.
    for (entry in serviceEntries) {
      val service = injector.getInstance(entry.key)
      builder.addService(entry.key, service)
    }
    for (edge in dependencies) {
      builder.addDependency(service = edge.service, dependency = edge.dependency)
    }
    for (edge in enhancements) {
      builder.enhanceService(service = edge.service, enhancement = edge.enhancement)
    }

    val serviceManager = builder.build()
    listeners.forEach { serviceManager.addListener(it) }
    return serviceManager
  }

  /** TODO: this should replace provideServiceManager(). */
  @Provides
  @Singleton
  internal fun provideServiceGraphBuilder(
    injector: Injector,
    serviceEntries: List<ServiceEntry>,
    dependencies: List<DependencyEdge>,
    enhancements: List<EnhancementEdge>
  ): ServiceGraphBuilder {
    // NB(mmihic): We get the binding for the Set<Service> because this uses a multibinder,
    // which allows us to retrieve the bindings for the elements
    val serviceListBinding = injector.getBinding(serviceSetKey)
    val invalidServices = serviceListBinding
        .acceptTargetVisitor(CheckServicesVisitor())
        .sorted()

    // Confirm all services have been registered as singleton. If they aren't singletons,
    // _readiness checks will fail
    check(invalidServices.isEmpty()) {
      "the following services are not marked as @Singleton: ${invalidServices.joinToString(", ")}"
    }

    val builder = ServiceGraphBuilder()
    for (entry in serviceEntries) {
      val service = injector.getInstance(entry.key)
      builder.addService(entry.key, service)
    }
    for (edge in dependencies) {
      builder.addDependency(service = edge.service, dependency = edge.dependency)
    }
    for (edge in enhancements) {
      builder.enhanceService(service = edge.service, enhancement = edge.enhancement)
    }
    return builder
  }

  @Suppress("DEPRECATION")
  private class CheckServicesVisitor :
      DefaultBindingTargetVisitor<Set<Service>, List<String>>(),
      MultibindingsTargetVisitor<Set<Service>, List<String>> {

    override fun visit(multibinding: MultibinderBinding<out Set<Service>>): List<String> {
      return multibinding.elements.asSequence()
          .filter { !Scopes.isSingleton(it) }
          .map { toHumanString(it) }
          .toList()
    }

    override fun visit(mapbinding: MapBinderBinding<out Set<Service>>): List<String> {
      return listOf()
    }

    override fun visit(optionalbinding: OptionalBinderBinding<out Set<Service>>): List<String> {
      return listOf()
    }

    private fun toHumanString(binding: Binding<*>): String {
      return when (binding) {
        is InstanceBinding<*> -> binding.instance.javaClass.canonicalName
        is ConstructorBinding<*> -> binding.constructor.declaringType.type.typeName
        is LinkedKeyBinding<*> -> binding.linkedKey.typeLiteral.type.typeName
        is ProviderInstanceBinding<*> -> binding.userSuppliedProvider.javaClass.canonicalName
        is ConvertedConstantBinding<*> -> binding.value.toString()
        else -> binding.provider.get().javaClass.canonicalName
      }
    }
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    val serviceSetKey = Key.get(Types.setOf(Service::class.java)) as Key<Set<Service>>
    private val log = KotlinLogging.logger {}
  }

}

internal data class DependencyEdge(val service: Key<*>, val dependency: Key<*>)
internal data class EnhancementEdge(val service: Key<*>, val enhancement: Key<*>)
internal data class ServiceEntry(val key: Key<out Service>)

inline fun <reified T : Service> ServiceModule(qualifier: KClass<out Annotation>? = null) =
    ServiceModule(T::class.toKey(qualifier))

/**
 * This module installs a service and hooks up its dependencies and enhancements.
 *
 * Here's how:
 *
 * ```
 * Guice.createInjector(object : KAbstractModule() {
 *   override fun configure() {
 *     install(ServiceModule<MyService>()
 *         .dependsOn<MyServiceDependency>())
 *     install(service<MyServiceDependency>())
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
 *     install(service<MyServiceDependency>(AnotherAnnotation::class))
 *   }
 * }
 * ```
 */
class ServiceModule(
  val key: Key<out Service>,
  val dependsOn: List<Key<out Service>> = listOf(),
  val enhancedBy: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to(key)

    multibind<ServiceEntry>().toInstance(ServiceEntry(key))

    for (dependsOnKey in dependsOn) {
      multibind<DependencyEdge>().toInstance(
          DependencyEdge(service = key, dependency = dependsOnKey)
      )
    }
    for (enhancedByKey in enhancedBy) {
      multibind<EnhancementEdge>().toInstance(
          EnhancementEdge(service = key, enhancement = enhancedByKey)
      )
    }
  }

  fun dependsOn(upstream: Key<out Service>) = ServiceModule(key, dependsOn + upstream, enhancedBy)

  fun enhancedBy(enhancement: Key<out Service>) =
      ServiceModule(key, dependsOn, enhancedBy + enhancement)

  inline fun <reified T : Service> dependsOn(qualifier: KClass<out Annotation>? = null) =
      dependsOn(T::class.toKey(qualifier))

  inline fun <reified T : Service> enhancedBy(qualifier: KClass<out Annotation>? = null) =
      enhancedBy(T::class.toKey(qualifier))
}