package misk.metrics.v2

import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples.Sample
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An in-memory metrics store with APIs to verify which metrics were collected.
 *
 * The only way to create an instance of this is with [FakeMetricsModule].
 */
@Singleton
class FakeMetrics @Inject internal constructor(
  private val registry: CollectorRegistry
) : Metrics {
  override fun counter(
    name: String,
    help: String,
    labelNames: List<String>
  ): Counter =
    Counter.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)

  override fun gauge(
    name: String,
    help: String,
    labelNames: List<String>
  ): Gauge =
    Gauge.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>
  ): Histogram =
    Histogram.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .buckets(*buckets.toDoubleArray())
      .register(registry)

  override fun summary(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?
  ): Summary =
    Summary.build(name, help)
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

  /** Returns a measurement for a [counter] or [gauge]. */
  fun get(name: String, vararg labels: Pair<String, String>): Double? =
    getSample(name, labels)?.value

  /** Returns the number of histogram samples taken. */
  fun summaryCount(name: String, vararg labels: Pair<String, String>): Double? =
    getSample(name, labels, sampleName = name + "_count")?.value

  /** Returns the sum of all histogram samples taken. */
  fun summarySum(name: String, vararg labels: Pair<String, String>): Double? =
    getSample(name, labels, sampleName = name + "_sum")?.value

  /** Returns the average of all histogram samples taken. */
  fun summaryMean(name: String, vararg labels: Pair<String, String>): Double? {
    val sum = summarySum(name, *labels) ?: return null
    val count = summaryCount(name, *labels) ?: return null
    return sum / count
  }

  /**
   * Returns the median for a [histogram]. In small samples this is the element preceding
   * the middle element.
   */
  fun summaryP50(name: String, vararg labels: Pair<String, String>): Double? =
    summaryQuantile(name, "0.5", *labels)

  /**
   * Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest
   * element.
   */
  fun summaryP99(name: String, vararg labels: Pair<String, String>): Double? =
    summaryQuantile(name, "0.99", *labels)

  fun summaryQuantile(
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
    sampleName: String? = null
  ): Sample? {
    val metricFamilySamples = registry.metricFamilySamples()
      .asSequence()
      .firstOrNull {
        it.name == name || (it.type == Collector.Type.COUNTER && "${it.name}_total" == name)
      }
      ?: return null

    val familySampleName = sampleName
      ?: if (metricFamilySamples.type == Collector.Type.COUNTER && !name.endsWith("_total")) {
        "${name}_total"
      } else {
        name
      }

    val labelNames = labels.map { it.first }
    val labelValues = labels.map { it.second }

    return metricFamilySamples.samples
      .firstOrNull {
        it.name == familySampleName && it.labelNames == labelNames && it.labelValues == labelValues
      }
  }

  fun getAllSamples(): Sequence<Sample> =
    registry.metricFamilySamples().asSequence().flatMap { it.samples }
}
