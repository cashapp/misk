package misk.metrics.v2

import misk.inject.KAbstractModule
import misk.metrics.MetricsModule

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    install(MetricsModule())
  }
}
