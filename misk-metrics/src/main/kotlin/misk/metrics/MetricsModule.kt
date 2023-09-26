package misk.metrics

import com.google.inject.Provider
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton

class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<CollectorRegistry>().toProvider(CollectorRegistryProvider::class.java).asSingleton()

  }

  /**
   * In order to make it possible to install this module multiple times, we make this binding not
   * dependent on the instance of [PrometheusMetricsClientModule] that created it.
   */
  internal class CollectorRegistryProvider @Inject constructor() : Provider<CollectorRegistry> {
    override fun get(): CollectorRegistry {
      return CollectorRegistry()
    }
  }
}
