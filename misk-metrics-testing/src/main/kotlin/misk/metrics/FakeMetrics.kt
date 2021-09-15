package misk.metrics

import io.prometheus.client.Collector.MetricFamilySamples.Sample
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram as PrometheusHistogram
import io.prometheus.client.Summary

/**
 * An in-memory metrics store with APIs to verify which metrics were collected.
 *
 * The only way to create an instance of this is with [FakeMetricsModule].
 */
class FakeMetrics internal constructor() : Metrics {
  private val registry = CollectorRegistry(true)

  override fun counter(name: String, help: String?, labelNames: List<String>): Counter =
    Counter.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)

  override fun gauge(name: String, help: String, labelNames: List<String>): Gauge =
    Gauge.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)

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
  ): PrometheusHistogram =
    PrometheusHistogram.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .buckets(*buckets.toDoubleArray())
      .register(registry)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Histogram {
    val summary = summary(name, help, labelNames, quantiles)
    return object : Histogram {
      override fun record(duration: Double, vararg labelValues: String) {
        summary.labels(*labelValues).observe(duration)
      }

      override fun count(vararg labelValues: String): Int =
        summary.labels(*labelValues).get().count.toInt()
    }
  }

  /** Returns a measurement for a [counter] or [gauge]. */
  fun get(name: String, vararg labels: Pair<String, String>): Double? =
    getSample(name, labels)?.value

  /** Returns the number of histogram samples taken. */
  fun histogramCount(name: String, vararg labels: Pair<String, String>): Double? =
    getSample(name, labels, sampleName = name + "_count")?.value

  /** Returns the sum of all histogram samples taken. */
  fun histogramSum(name: String, vararg labels: Pair<String, String>): Double? =
    getSample(name, labels, sampleName = name + "_sum")?.value

  /** Returns the average of all histogram samples taken. */
  fun histogramMean(name: String, vararg labels: Pair<String, String>): Double? {
    val sum = histogramSum(name, *labels) ?: return null
    val count = histogramCount(name, *labels) ?: return null
    return sum / count
  }

  /**
   * Returns the median for a [histogram]. In small samples this is the element preceding
   * the middle element.
   */
  fun histogramP50(name: String, vararg labels: Pair<String, String>): Double? =
    histogramQuantile(name, "0.5", *labels)

  /**
   * Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest
   * element.
   */
  fun histogramP99(name: String, vararg labels: Pair<String, String>): Double? =
    histogramQuantile(name, "0.99", *labels)

  fun histogramQuantile(
    name: String,
    quantile: String,
    vararg labels: Pair<String, String>
  ): Double? {
    val extraLabels = labels.toList() + ("quantile" to quantile)
    return getSample(name, extraLabels.toTypedArray())?.value
  }

  fun getSample(
    name: String,
    labels: Array<out Pair<String, String>>,
    sampleName: String = name
  ): Sample? {
    val metricFamilySamples = registry.metricFamilySamples()
      .asSequence()
      .firstOrNull { it.name == name }
      ?: return null

    val labelNames = labels.map { it.first }
    val labelValues = labels.map { it.second }

    return metricFamilySamples.samples
      .firstOrNull {
        it.name == sampleName && it.labelNames == labelNames && it.labelValues == labelValues
      }
  }
}
