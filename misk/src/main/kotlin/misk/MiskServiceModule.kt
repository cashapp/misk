package misk

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.Scopes
import misk.concurrent.SleeperModule
import misk.environment.RealEnvVarModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
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
    val invalidServices = mutableListOf<String>()
    val builder = ServiceGraphBuilder()

    // Support the new ServiceModule API.
    for (entry in serviceEntries) {
      if (!Scopes.isSingleton(injector.getBinding(entry.key))) {
        invalidServices += entry.key.typeLiteral.type.typeName
      }
      builder.addService(entry.key, injector.getProvider(entry.key))
    }
    for (edge in dependencies) {
      builder.addDependency(dependent = edge.dependent, dependsOn = edge.dependsOn)
    }
    for (edge in enhancements) {
      builder.enhanceService(toBeEnhanced = edge.toBeEnhanced, enhancement = edge.enhancement)
    }

    // Confirm all services have been registered as singleton. If they aren't singletons,
    // _readiness checks will fail
    check(invalidServices.isEmpty()) {
      "the following services are not marked as @Singleton: ${invalidServices.joinToString(", ")}"
    }

    // Enforce that services are installed via a ServiceModule.
    check(services.isEmpty()) {
      "This doesn't work anymore! " +
          "Instead of using `multibind<Service>().to(${services.first()::class.simpleName})`, " +
          "use `install(ServiceModule<${services.first()::class.simpleName}>())`."
    }

    val serviceManager = builder.build()
    listeners.forEach { serviceManager.addListener(it) }
    return serviceManager
  }

  companion object {
    private val log = KotlinLogging.logger {}
  }
}