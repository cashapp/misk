package misk.metrics

import misk.inject.KAbstractModule
import misk.metrics.backends.prometheus.PrometheusMetricsClientModule

@Deprecated("install PrometheusMetricsClientModule instead")
class MetricsModule : KAbstractModule() {
  override fun configure() {
    install(PrometheusMetricsClientModule())
  }
}