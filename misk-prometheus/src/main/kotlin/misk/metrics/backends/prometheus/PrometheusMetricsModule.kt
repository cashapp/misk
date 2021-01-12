package misk.metrics.backends.prometheus

import misk.inject.KAbstractModule

@Deprecated("Install PrometheusMetricsServiceModule instead")
class PrometheusMetricsModule(private val config: PrometheusConfig) : KAbstractModule() {
  override fun configure() {
    install(PrometheusMetricsServiceModule(config))
  }
}
