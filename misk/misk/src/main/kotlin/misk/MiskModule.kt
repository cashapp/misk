package misk

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provides
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.metrics.MetricsModule
import misk.moshi.MoshiModule
import misk.time.ClockModule
import javax.inject.Singleton

class MiskModule : KAbstractModule() {
  override fun configure() {
    binder().disableCircularProxies()
    binder().requireExactBindingAnnotations()
    install(MetricsModule())
    install(ClockModule())
    install(MoshiModule())

    // Initialize empty sets for our multibindings.
    newMultibinder<HealthCheck>()
    newMultibinder<Service>()
  }

  @Provides
  @Singleton
  fun provideServiceManager(services: List<Service>): ServiceManager {
    return CoordinatedService.coordinate(services)
  }
}
