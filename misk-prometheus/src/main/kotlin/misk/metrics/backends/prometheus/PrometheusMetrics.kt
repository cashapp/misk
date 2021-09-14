package misk.metrics.backends.prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import misk.metrics.Histogram as MiskHistogram
import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accepts metrics and writes them to the Prometheus [CollectorRegistry].
 */
@Singleton
internal class PrometheusMetrics @Inject internal constructor(
  private val registry: CollectorRegistry
) : Metrics {
  override fun counter(
    name: String,
    help: String?,
    labelNames: List<String>
  ): Counter =
    Counter.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)

  override fun gauge(
    name: String,
    help: String,
    labelNames: List<String>
  ): Gauge =
    Gauge.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): MiskHistogram =
    PrometheusHistogram(summary(name, help, labelNames, quantiles))

  override fun summary(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Summary =
    Summary.build(name, help)
    .labelNames(*labelNames.toTypedArray())
    .apply {
      quantiles.forEach { (key, value) ->
        quantile(key, value)
      }
    }
    .register(registry)

  override fun distribution(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>
  ): Histogram =
    Histogram.build(name, help)
    .labelNames(*labelNames.toTypedArray())
    .buckets(*buckets.toDoubleArray())
    .register(registry)

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
