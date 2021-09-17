package misk.metrics.v2

import io.prometheus.client.CollectorRegistry
import misk.inject.KAbstractModule

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<CollectorRegistry>().toInstance(CollectorRegistry(true))
    bind<Metrics>().to<FakeMetrics>()
  }
}
