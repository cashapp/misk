package misk.metrics.backends.prometheus

import io.prometheus.client.CollectorRegistry
import misk.metrics.Metrics
import misk.metrics.v2.Metrics as MetricsV2
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Accepts metrics and writes them to the Prometheus [CollectorRegistry].
 */
@Deprecated(
  message = "Misk Metrics V1 is Deprecated, please use V2",
  replaceWith = ReplaceWith("misk.metrics.backends.prometheus.v2.PrometheusMetrics"),
  level = DeprecationLevel.WARNING
)
@Singleton
internal class PrometheusMetrics @Inject internal constructor(
  private val metricsV2: MetricsV2
) : Metrics(metricsV2) {
  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
