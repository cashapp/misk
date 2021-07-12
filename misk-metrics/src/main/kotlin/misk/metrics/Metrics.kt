package misk.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Gauge

/**
 * Interface for application code to emit metrics to a metrics backend like Prometheus.
 *
 * Tests that use this should install a metrics client like `PrometheusMetricsClientModule`.
 * Services that use this should install a metrics service like `PrometheusMetricsServiceModule`.
 */
interface Metrics {
  fun counter(
    name: String,
    help: String? = "",
    labelNames: List<String> = listOf()
  ): Counter

  fun gauge(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf()
  ): Gauge

  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String>,
    quantiles: Map<Double, Double> = defaultQuantiles
  ): Histogram
}

internal val defaultQuantiles = mapOf(
  0.5 to 0.05,
  0.75 to 0.02,
  0.95 to 0.01,
  0.99 to 0.001,
  0.999 to 0.0001
)
