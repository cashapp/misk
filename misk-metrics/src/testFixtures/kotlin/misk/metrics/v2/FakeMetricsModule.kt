package misk.metrics.v2

import io.prometheus.client.CollectorRegistry
import misk.inject.KAbstractModule

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    install(CollectorRegistryModule())
    bind<Metrics>().to<FakeMetrics>()
  }
}
