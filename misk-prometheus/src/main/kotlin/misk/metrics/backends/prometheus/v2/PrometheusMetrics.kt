package misk.metrics.backends.prometheus.v2

import ComputedGauge
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import misk.metrics.v2.Metrics
import misk.metrics.v2.PeakGauge
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
    help: String,
    labelNames: List<String>
  ): Counter = Counter
    .build(name, help)
    .labelNames(*labelNames.toTypedArray())
    .register(registry)

  override fun gauge(
    name: String,
    help: String,
    labelNames: List<String>
  ): Gauge = Gauge
    .build(name, help)
    .labelNames(*labelNames.toTypedArray())
    .register(registry)

  override fun peakGauge(name: String,
    help: String,
    labelNames: List<String>
  ): PeakGauge = PeakGauge
    .builder(name, help)
    .labelNames(*labelNames.toTypedArray())
    .register(registry)

  override fun computedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
    valueSupplier: ComputedGauge.ValueSupplier
  ): ComputedGauge =
    ComputedGauge.builder(name, help, valueSupplier).labelNames(*labelNames.toTypedArray())
      .register(registry)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>
  ): Histogram = Histogram
    .build(name, help)
    .labelNames(*labelNames.toTypedArray())
    .buckets(*buckets.toDoubleArray())
    .register(registry)

  override fun summary(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ): Summary = Summary
    .build(name, help)
    .labelNames(*labelNames.toTypedArray())
    .apply {
      quantiles.forEach { (key, value) ->
        quantile(key, value)
      }
    }
    .apply {
      if (maxAgeSeconds != null) {
        this.maxAgeSeconds(maxAgeSeconds)
      }
    }
    .register(registry)

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
