package misk

import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.Scopes
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metadata.servicegraph.ServiceGraphMetadata
import misk.metadata.servicegraph.ServiceGraphMetadataProvider
import misk.web.metadata.MetadataModule
import misk.logging.getLogger

class ServiceManagerModule @JvmOverloads constructor(
  private val serviceManagerConfig: ServiceManagerConfig = ServiceManagerConfig()
) : KAbstractModule() {

  companion object {
    private val log = getLogger<ServiceManagerModule>()
  }

  override fun configure() {
    newMultibinder<Service>()
    newMultibinder<ServiceManager.Listener>()

    multibind<ServiceManager.Listener>().toProvider {
      object : ServiceManager.Listener() {
        override fun failure(service: Service) {
          log.error(service.failureCause()) { "Service $service failed" }
        }
      }
    }.asSingleton()
    newMultibinder<ServiceEntry>()
    newMultibinder<DependencyEdge>()
    newMultibinder<EnhancementEdge>()

    install(MetadataModule(ServiceGraphMetadataProvider()))
    bind<ServiceGraphMetadata>().toProvider(ServiceGraphMetadataProvider())
  }

  @Provides
  @Singleton
  internal fun provideServiceManager(
    builder: ServiceGraphBuilder,
    listeners: List<ServiceManager.Listener>,
  ): ServiceManager {
    val serviceManager = builder.build()

    if (serviceManagerConfig.debug_service_graph) {
      log.info { "Service dependency graph:\n$builder" }
    }

    listeners.forEach { serviceManager.addListener(it, directExecutor()) }
    return serviceManager
  }

  @Provides
  @Singleton
  internal fun provideServiceGraphBuilder(
    injector: Injector,
    services: List<Service>,
    serviceEntries: List<ServiceEntry>,
    dependencies: List<DependencyEdge>,
    enhancements: List<EnhancementEdge>
  ): ServiceGraphBuilder {
    val invalidServices = mutableListOf<String>()
    val builder = ServiceGraphBuilder()

    // Support the new ServiceModule API.
    for (entry in serviceEntries) {
      if (!Scopes.isSingleton(injector.getBinding(entry.key))) {
        invalidServices += entry.key.typeLiteral.type.typeName
      }
      builder.addService(entry.key, entry.key.typeLiteral.toString(), injector.getProvider(entry.key))
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

    // Run validation within the build method
    builder.build()

    return builder
  }

  @Provides
  @Singleton
  internal fun provideServiceGraphMetadata(builder: ServiceGraphBuilder) = builder.toMetadata()
}
