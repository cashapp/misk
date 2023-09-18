package misk.metrics

import com.google.inject.Provider
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metrics.v2.Metrics as V2Metrics
import misk.metrics.v3.Metrics as V3Metrics

class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<CollectorRegistry>().toProvider(CollectorRegistryProvider::class.java).asSingleton()
    bind<Metrics>().toProvider(MetricsProvider::class.java).asSingleton()
    bind<V2Metrics>().toProvider(V2MetricsProvider::class.java).asSingleton()
    bind<V3Metrics>().toProvider(V3MetricsProvider::class.java).asSingleton()
    bind<MeterRegistry>().toProvider(MeterRegistryProvider::class.java).asSingleton()

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

  internal class MetricsProvider @Inject constructor(private val v2Metrics: V2Metrics) :
    Provider<Metrics> {
    override fun get(): Metrics {
      return Metrics.factory(v2Metrics)
    }
  }

  internal class V2MetricsProvider @Inject constructor(
    private val registry: CollectorRegistry,
    private val metricsV3: V3Metrics
  ) : Provider<V2Metrics> {
    override fun get(): V2Metrics {
      return V2Metrics.factory(registry, metricsV3)
    }
  }

  internal class MeterRegistryProvider @Inject constructor(
    private val registry: CollectorRegistry,
  ) : Provider<PrometheusMeterRegistry> {
    override fun get(): PrometheusMeterRegistry {
      return PrometheusMeterRegistry(PrometheusConfig.DEFAULT,registry, Clock.SYSTEM)
    }
  }
  internal class V3MetricsProvider @Inject constructor(
    private val registry: MeterRegistry,
  ) : Provider<V3Metrics> {
    override fun get(): V3Metrics {
      return V3Metrics(registry)
    }
  }
}
