package misk.metrics.backends.prometheus

import misk.inject.KAbstractModule
import misk.metrics.Metrics
import misk.metrics.MetricsModule

/**
 * Binds a [Metrics] implementation whose metrics don't write to a Prometheus infrastructure. For that you should
 * install [PrometheusMetricsServiceModule].
 */
@Deprecated("use MetricsModule instead")
class PrometheusMetricsClientModule : KAbstractModule() {
  override fun configure() {
    install(MetricsModule())
  }
}
