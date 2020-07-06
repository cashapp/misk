package misk.metrics.backends.prometheus

import io.prometheus.client.CollectorRegistry
import misk.inject.KAbstractModule
import misk.metrics.HistogramRegistry
import misk.metrics.Metrics
import misk.prometheus.PrometheusHistogramRegistry

/**
 * Binds a [Metrics] implementation whose metrics don't write to a Prometheus infrastructure. For
 * that you should install [PrometheusMetricsServiceModule].
 */
class PrometheusMetricsClientModule : KAbstractModule() {
  override fun configure() {
    bind<HistogramRegistry>().to<PrometheusHistogramRegistry>()
    bind<Metrics>().to<PrometheusMetrics>()
    bind<CollectorRegistry>().toInstance(CollectorRegistry())
  }

  // Override equals() and hashCode() so this module can be installed multiple times.
  override fun equals(other: Any?) = other is PrometheusMetricsClientModule
  override fun hashCode() = PrometheusMetricsClientModule::class.hashCode()
}