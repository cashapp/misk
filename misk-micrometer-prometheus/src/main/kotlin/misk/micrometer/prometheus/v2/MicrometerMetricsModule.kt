package misk.micrometer.prometheus.v2

import com.google.inject.Provider
import io.micrometer.core.instrument.MeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metrics.v2.Metrics

class MicrometerMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().toProvider(MicrometerMetricsProvider::class.java).asSingleton()
    bind<misk.metrics.Metrics>().toProvider(V1MetricsProvider::class.java).asSingleton()
  }

  internal class MicrometerMetricsProvider
  @Inject
  constructor(private val meterRegistry: MeterRegistry, private val collectorRegistry: CollectorRegistry) :
    Provider<Metrics> {
    override fun get(): Metrics {
      return MicrometerMetrics(meterRegistry, collectorRegistry)
    }
  }

  internal class V1MetricsProvider @Inject constructor(private val v2Metrics: Metrics) :
    Provider<misk.metrics.Metrics> {
    override fun get(): misk.metrics.Metrics {
      return misk.metrics.Metrics.factory(v2Metrics)
    }
  }
}
