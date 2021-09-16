package misk.metrics

import io.prometheus.client.CollectorRegistry
import misk.inject.KAbstractModule
import javax.inject.Inject
import javax.inject.Provider
import misk.metrics.v2.FakeMetricsModule as FakeMetricsModuleV2

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().to<FakeMetrics>()

    install(FakeMetricsModuleV2())
  }
}
