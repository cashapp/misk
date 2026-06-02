package misk.metrics

import misk.metrics.v2.Metrics

/**
 * Interface for application code to emit metrics to a metrics backend like Prometheus.
 *
 * Tests that use this should install a metrics client like `PrometheusMetricsClientModule`. Services that use this
 * should install a metrics service like `PrometheusMetricsServiceModule`.
 */
@Deprecated(
  message = "Misk Metrics V1 is Deprecated, please use V2",
  replaceWith = ReplaceWith("misk.metrics.v2.Metrics"),
  level = DeprecationLevel.WARNING,
)
interface Metrics {
  fun getMetrics(): Metrics

  /**
   * counter creates and registers a new `Counter` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Counter.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus. Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  @Deprecated(message = "Misk Metrics V1 is Deprecated, please use V2", level = DeprecationLevel.WARNING)
  fun counter(name: String, help: String, labelNames: List<String> = listOf()) =
    getMetrics().counter(name, help, labelNames)

  /**
   * gauge creates and registers a new `Gauge` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Gauge.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus. Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  @Deprecated(message = "Misk Metrics V1 is Deprecated, please use V2", level = DeprecationLevel.WARNING)
  fun gauge(name: String, help: String = "", labelNames: List<String> = listOf()) =
    getMetrics().gauge(name, help, labelNames)

  /**
   * histogram creates and registers a new `Summary` prometheus type.
   *
   * For legacy reasons this function is called histogram(...) but it's not backed by a histogram because of issues with
   * the previous time series backend.
   *
   * If you're using this metric type, you likely want a real Histogram instead of a Summary. To change to histogram
   * type, you need to create a different metric (with another name) as the data structure used by the time series
   * database is incompatible and can break existing dashboards and monitors.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Summary.html or
   * https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus. Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param quantiles is a map of all of the quantiles (a.k.a. percentiles) that will be computed for the metric. The
   *   key of the map is the quantile as a ratio (e.g. 0.99 represents p99) and the value is the "tolerable error" of
   *   the computed quantile.
   */
  @Deprecated(
    message = "Recommend migrating to misk.metrics.v2.Metrics.histogram. See kdoc for detail",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("legacyHistogram(name,help,labelNames,quantiles,maxAgeSeconds)"),
  )
  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    quantiles: Map<Double, Double> = defaultQuantiles,
    maxAgeSeconds: Long? = null,
  ) = legacyHistogram(name, help, labelNames, quantiles, maxAgeSeconds)

  /**
   * histogram creates and registers a new `Summary` prometheus type.
   *
   * For legacy reasons this function is called histogram(...) but it's not backed by a histogram because of issues with
   * the previous time series backend.
   *
   * If you're using this metric type, you likely want a real Histogram instead of a Summary. To change to histogram
   * type, you need to create a different metric (with another name) as the data structure used by the time series
   * database is incompatible and can break existing dashboards and monitors.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Summary.html or
   * https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus. Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param quantiles is a map of all of the quantiles (a.k.a. percentiles) that will be computed for the metric. The
   *   key of the map is the quantile as a ratio (e.g. 0.99 represents p99) and the value is the "tolerable error" of
   *   the computed quantile.
   */
  @Deprecated(
    message = "Recommend migrating to misk.metrics.v2.Metrics.histogram. See kdoc for detail",
    level = DeprecationLevel.WARNING,
  )
  fun legacyHistogram(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    quantiles: Map<Double, Double> = defaultQuantiles,
    maxAgeSeconds: Long? = null,
  ) = getMetrics().legacyHistogram(name, help, labelNames, quantiles, maxAgeSeconds)

  companion object {
    fun factory(metrics: Metrics) =
      object : misk.metrics.Metrics {
        override fun getMetrics() = metrics
      }
  }
}

val defaultQuantiles = mapOf(0.5 to 0.05, 0.75 to 0.02, 0.95 to 0.01, 0.99 to 0.001, 0.999 to 0.0001)
