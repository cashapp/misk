package misk.metrics

import misk.inject.KAbstractModule
import misk.metrics.v2.FakeMetricsModule as FakeMetricsModuleV2

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().to<FakeMetrics>()

    install(FakeMetricsModuleV2())
  }
}
