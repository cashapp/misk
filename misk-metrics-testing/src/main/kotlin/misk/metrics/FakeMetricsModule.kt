package misk.metrics

import misk.inject.KAbstractModule
import misk.metrics.v2.FakeMetricsModule as FakeMetricsModuleV2

@Deprecated("Replace the dependency on misk-metrics-testing with testFixtures(misk-metrics)")
class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().to<FakeMetrics>()

    install(FakeMetricsModuleV2())
  }
}
