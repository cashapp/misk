package misk.metrics

import misk.inject.KAbstractModule

class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    install(MetricsModule())
  }
}
