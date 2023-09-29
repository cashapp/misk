package misk.metrics

import com.google.inject.Provider
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metrics.backends.prometheus.PrometheusMetricsClientModule
import misk.metrics.v2.Metrics as V2Metrics

class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<CollectorRegistry>().toProvider(CollectorRegistryProvider::class.java).asSingleton()
    bind<HistogramRegistry>().toProvider(HistogramRegistryProvider::class.java).asSingleton()
    bind<Metrics>().toProvider(MetricsProvider::class.java).asSingleton()
    bind<V2Metrics>().toProvider(V2MetricsProvider::class.java).asSingleton()
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

  internal class HistogramRegistryProvider @Inject constructor(private val metrics: Metrics) : Provider<HistogramRegistry> {
    override fun get(): HistogramRegistry {
      return HistogramRegistry.factory(metrics)
    }
  }
  internal class MetricsProvider @Inject constructor(private val v2Metrics:V2Metrics) : Provider<Metrics> {
    override fun get(): Metrics {
      return Metrics.factory(v2Metrics)
    }
  }
  internal class V2MetricsProvider @Inject constructor(private val registry: CollectorRegistry) : Provider<V2Metrics> {
    override fun get(): V2Metrics {
      return V2Metrics.factory(registry)
    }
  }

}
