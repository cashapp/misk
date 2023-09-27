package misk.metrics.backends.prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import misk.metrics.Histogram
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
) : Metrics {
  @Deprecated(
    message = "Misk Metrics V1 is Deprecated, please use V2",
    level = DeprecationLevel.WARNING
  )
  override fun counter(
    name: String,
    help: String,
    labelNames: List<String>
  ): Counter = metricsV2.counter(name, help, labelNames)

  @Deprecated(
    message = "Misk Metrics V1 is Deprecated, please use V2",
    level = DeprecationLevel.WARNING
  )
  override fun gauge(
    name: String,
    help: String,
    labelNames: List<String>
  ): Gauge = metricsV2.gauge(name, help, labelNames)

  @Deprecated(
    message = "Recommend migrating to misk.metrics.v2.Metrics.histogram. See kdoc for detail",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("legacyHistogram(name,help,labelNames,quantiles,maxAgeSeconds)")
  )
  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ): Histogram = legacyHistogram(name, help, labelNames, quantiles, maxAgeSeconds)

  @Deprecated(
    message = "Recommend migrating to misk.metrics.v2.Metrics.histogram. See kdoc for detail",
    level = DeprecationLevel.WARNING
  )
  override fun legacyHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?
  ): Histogram = metricsV2.legacyHistogram(name, help, labelNames, quantiles, maxAgeSeconds)

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
