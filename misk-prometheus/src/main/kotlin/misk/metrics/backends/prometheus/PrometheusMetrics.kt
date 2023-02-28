package misk.metrics.backends.prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import misk.metrics.Histogram
import misk.metrics.Metrics
import misk.metrics.v2.PeakGauge
import misk.metrics.v2.Metrics as MetricsV2
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accepts metrics and writes them to the Prometheus [CollectorRegistry].
 */
@Singleton
internal class PrometheusMetrics @Inject internal constructor(
  private val metricsV2: MetricsV2
) : Metrics {
  override fun counter(
    name: String,
    help: String,
    labelNames: List<String>
  ): Counter = metricsV2.counter(name, help, labelNames)

  override fun gauge(
    name: String,
    help: String,
    labelNames: List<String>
  ): Gauge = metricsV2.gauge(name, help, labelNames)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ): Histogram = PrometheusHistogram(metricsV2.summary(name, help, labelNames, quantiles, maxAgeSeconds))

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
