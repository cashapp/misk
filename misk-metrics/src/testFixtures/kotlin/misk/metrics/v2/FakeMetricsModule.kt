package misk.metrics.v2

import io.prometheus.client.CollectorRegistry
import misk.inject.KInstallOnceModule

class FakeMetricsModule : KInstallOnceModule() {
  override fun configure() {
    bind<CollectorRegistry>().toInstance(CollectorRegistry(true))
    bind<Metrics>().to<FakeMetrics>()
  }
}
