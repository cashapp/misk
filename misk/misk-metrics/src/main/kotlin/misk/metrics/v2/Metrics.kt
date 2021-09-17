package misk.metrics.v2

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import io.prometheus.client.Histogram

/**
 * Interface for application code to emit metrics to a metrics backend like Prometheus.
 *
 * NOTE: `misk.metrics.v2.Metrics` is NOT backward compatible with `misk.metrics.Metrics`.
 *   This is because the metric type of the `histogram(...)` function has changed.
 *   If a callsite which used `misk.metrics.Metrics.histogram(...)` is upgraded to use
 *   `misk.metrics.v2.Metrics.histogram(...)`, the dashboards and monitors based on the
 *   metric will break because the data type of the metric will have changed.
 *
 * Tests that use this should install a metrics client like `PrometheusMetricsClientModule`.
 * Services that use this should install a metrics service like `PrometheusMetricsServiceModule`.
 */
interface Metrics {
  /**
   * counter creates and registers a new `Counter` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Counter.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  fun counter(
    name: String,
    help: String? = "",
    labelNames: List<String> = listOf()
  ): Counter

  /**
   * gauge creates and registers a new `Gauge` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Gauge.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  fun gauge(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf()
  ): Gauge

  /**
   * histogram creates a new `Histogram` prometheus type with the supplied parameters.
   *
   * NOTE: `misk.metrics.v2.Metrics` is NOT backward compatible with `misk.metrics.Metrics`.
   *   This is because the metric type of the `histogram(...)` function has changed.
   *   If a callsite which used `misk.metrics.Metrics.histogram(...)` is upgraded to use
   *   `misk.metrics.v2.Metrics.histogram(...)`, the dashboards and monitors based on the
   *   metric will break because the data type of the metric will have changed.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param buckets a list of upper bounds of buckets for the histogram.
   */
  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String>,
    buckets: List<Double> = defaultBuckets
  ): Histogram

  /**
   * summary creates and registers a new `Summary` prometheus type.
   *
   * This function used to be called `histogram(...)` but was updated because it's not backed
   * by a histogram which is confusing.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Summary.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param quantiles is a map of all of the quantiles (a.k.a. percentiles) that will be computed
   *  for the metric. The key of the map is the quantile as a ratio (e.g. 0.99 represents p99) and
   *  the value is the "tolerable error" of the computed quantile.
   */
  fun summary(
    name: String,
    help: String = "",
    labelNames: List<String>,
    quantiles: Map<Double, Double> = defaultQuantiles
  ): Summary
}

val defaultQuantiles = mapOf(
  0.5 to 0.05,
  0.75 to 0.02,
  0.95 to 0.01,
  0.99 to 0.001,
  0.999 to 0.0001
)

/**
 * defaultSparseBuckets is a default set of buckets which assumes the value is in milliseconds (ms).
 *
 * It contains 21 buckets which range from 1ms to 8m.
 *
 * Adapted from the default M3 buckets.
 *
 * https://github.com/m3db/m3/blob/v1.1.0/src/x/instrument/methods.go#L85-L147
 */
val defaultSparseBuckets = listOf(
  1.0,
  5.0,
  10.0,
  25.0,
  50.0,
  75.0,
  100.0,
  250.0,
  500.0,
  750.0,
  1000.0,
  2500.0,
  5000.0,
  7500.0,
  10000.0,
  25000.0,
  50000.0,
  75000.0,
  100000.0,
  250000.0,
  500000.0,
)

/**
 * defaultBuckets is a default set of buckets which assumes the value is in milliseconds (ms).
 *
 * It contains 58 buckets which range from 1ms to 1hr.
 *
 * Adapted from the default M3 buckets.
 *
 * https://github.com/m3db/m3/blob/v1.1.0/src/x/instrument/methods.go#L57-L83
 */
val defaultBuckets = listOf(
  1.0,
  2.0,
  4.0,
  6.0,
  8.0,
  10.0,
  20.0,
  40.0,
  60.0,
  80.0,
  100.0,
  200.0,
  400.0,
  600.0,
  800.0,
  1000.0,
  1500.0,
  2000.0,
  2500.0,
  3000.0,
  3500.0,
  4000.0,
  4500.0,
  5000.0,
  5500.0,
  6000.0,
  6500.0,
  7000.0,
  7500.0,
  8000.0,
  8500.0,
  9000.0,
  9500.0,
  10000.0,
  15000.0,
  20000.0,
  25000.0,
  30000.0,
  35000.0,
  40000.0,
  45000.0,
  50000.0,
  55000.0,
  60000.0,
  150000.0,
  300000.0,
  450000.0,
  600000.0,
  900000.0,
  1200000.0,
  1500000.0,
  1800000.0,
  2100000.0,
  2400000.0,
  2700000.0,
  3000000.0,
  3300000.0,
  3600000.0,
)

/**
 * Generate a list of upper bounds of buckets for a histogram with a linear sequence.
 */
fun linearBuckets(
  start: Double,
  width: Double,
  count: Int,
): List<Double> {
  return generateSequence(start) { it + width }.take(count).toList()
}

/**
 * Generate a list of upper bounds of buckets for a histogram with an exponential sequence.
 */
fun exponentialBuckets(
  start: Double,
  factor: Double,
  count: Int,
): List<Double> {
  return generateSequence(start) { it * factor }.take(count).toList()
}
