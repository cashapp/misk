package misk.metrics

import io.prometheus.client.CollectorRegistry
import misk.inject.KAbstractModule

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    val collectorRegistry = CollectorRegistry(true)
    bind<CollectorRegistry>().toInstance(collectorRegistry)
    bind<Metrics>().to<FakeMetrics>()
    bind<FakeMetrics>().toInstance(FakeMetrics(collectorRegistry))
  }
}
