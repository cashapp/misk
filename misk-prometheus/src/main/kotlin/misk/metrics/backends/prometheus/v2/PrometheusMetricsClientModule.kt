package misk.metrics.backends.prometheus.v2

import misk.inject.KAbstractModule
import misk.metrics.MetricsModule
import misk.metrics.v2.Metrics

/** Binds a [Metrics] implementation. */
@Deprecated("use MetricsModule instead")
class PrometheusMetricsClientModule : KAbstractModule() {
  override fun configure() {
    install(MetricsModule())
  }
}
