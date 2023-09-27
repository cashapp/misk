package misk.metrics.backends.prometheus.v2

import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics

/**
 * Binds a [Metrics] implementation.
 */
class PrometheusMetricsClientModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().to<PrometheusMetrics>()
  }
}
