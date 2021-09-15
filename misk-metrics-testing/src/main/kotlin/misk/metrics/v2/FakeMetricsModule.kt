package misk.metrics.v2

import misk.inject.KAbstractModule

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<FakeMetrics>().toInstance(FakeMetrics())
    bind<Metrics>().to<FakeMetrics>()
  }
}
