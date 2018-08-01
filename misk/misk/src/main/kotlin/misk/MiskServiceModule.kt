package misk

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provides
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.metrics.MetricsModule
import misk.moshi.MoshiModule
import misk.resources.ResourceLoaderModule
import misk.time.ClockModule
import misk.tokens.TokenGeneratorModule
import javax.inject.Singleton

class MiskServiceModule : KAbstractModule() {
  override fun configure() {
    binder().disableCircularProxies()
    binder().requireExactBindingAnnotations()
    install(MetricsModule())
    install(ClockModule())
    install(MoshiModule())
    install(ResourceLoaderModule())
    install(TokenGeneratorModule())

    // Initialize empty sets for our multibindings.
    newMultibinder<HealthCheck>()
    newMultibinder<Service>()
  }

  @Provides
  @Singleton
  fun provideServiceManager(services: List<Service>): ServiceManager {
    // Confirm all services have been registered as singleton. If they aren't singletons,
    // _readiness checks will fail
    val invalidServices = services.map { it.javaClass }.filter { !isSingleton(it) }.map { it.name }
    check(invalidServices.isEmpty()) {
      "the following services are not marked as @Singleton: ${invalidServices.sorted().joinToString(
          ", ")}"
    }

    return CoordinatedService.coordinate(services)
  }

  private fun isSingleton(clazz: Class<*>) =
      clazz.getAnnotation(com.google.inject.Singleton::class.java) != null ||
          clazz.getAnnotation(javax.inject.Singleton::class.java) != null
}
