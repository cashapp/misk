package misk

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.AbstractModule
import com.google.inject.Provides

import misk.healthchecks.HealthChecksModule
import misk.metrics.MetricsModule
import misk.time.ClockModule
import javax.inject.Singleton

class MiskModule : AbstractModule() {
  override fun configure() {
    install(HealthChecksModule())
    install(MetricsModule())
    install(ClockModule())
  }

  @Provides
  @Singleton
  fun provideServiceManager(services: List<Service>): ServiceManager {
    return ServiceManager(services)
  }
}
