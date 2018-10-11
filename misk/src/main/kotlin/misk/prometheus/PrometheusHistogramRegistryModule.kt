package misk.prometheus

import misk.inject.KAbstractModule
import misk.metrics.HistogramRegistry

class PrometheusHistogramRegistryModule : KAbstractModule() {
  override fun configure() {
    bind<HistogramRegistry>().to<PrometheusHistogramRegistry>()
  }
}