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
    help: String,
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
   * histogram creates and registers a new `Summary` prometheus type.
   *
   * Deprecated: if you really need a summary metric, use [misk.metrics.v2.Metrics.summary] instead.
   *
   * For legacy reasons, this function is called `histogram(...)` but it's not backed
   * by a histogram cause constraints in our previous Metrics vendor which is confusing.
   *
   * If you're using this metric type, you likely want a real Histogram instead of a Summary.
   * To change to histogram type, you need to create a different metric (with another name) as the
   * data structure used by the time series database is incompatible and can break existing dashboards
   * and monitors. [MetricsInterceptor](misk/web/interceptors/MetricsInterceptor.kt) is an example
   * of migrating to v2.Histogram.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Summary.html or
   * https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param help human-readable help text that will be supplied to prometheus.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param quantiles is a map of all of the quantiles (a.k.a. percentiles) that will be computed
   *  for the metric. The key of the map is the quantile as a ratio (e.g. 0.99 represents p99) and
   *  the value is the "tolerable error" of the computed quantile.
   */
  @Deprecated("Use misk.metrics.v2.Metrics.summary instead")
  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String>,
    quantiles: Map<Double, Double> = defaultQuantiles,
    maxAgeSeconds: Long? = null
  ): Histogram
}

val defaultQuantiles = mapOf(
  0.5 to 0.05,
  0.75 to 0.02,
  0.95 to 0.01,
  0.99 to 0.001,
  0.999 to 0.0001
)
