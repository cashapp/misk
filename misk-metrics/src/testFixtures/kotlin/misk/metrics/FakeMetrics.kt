package misk.metrics

import io.prometheus.client.*
import io.prometheus.client.Collector.MetricFamilySamples.Sample
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * An in-memory metrics store with APIs to verify which metrics were collected.
 *
 * The only way to create an instance of this is with [FakeMetricsModule].
 */
@Deprecated(
  message = "Misk Metrics V1 is Deprecated, please use V2",
  replaceWith = ReplaceWith("misk.metrics.v2.FakeMetrics"),
  level = DeprecationLevel.WARNING,
)
@Singleton
class FakeMetrics @Inject internal constructor(private val registry: CollectorRegistry) : Metrics {

  private val v2Metrics = misk.metrics.v2.Metrics.factory(registry)

  override fun getMetrics() = v2Metrics

  @Deprecated(message = "Misk Metrics V1 is Deprecated, please use V2", level = DeprecationLevel.WARNING)
  override fun counter(name: String, help: String, labelNames: List<String>) = super.counter(name, help, labelNames)

  @Deprecated(message = "Misk Metrics V1 is Deprecated, please use V2", level = DeprecationLevel.WARNING)
  override fun gauge(name: String, help: String, labelNames: List<String>) = super.gauge(name, help, labelNames)

  @Deprecated(
    message = "Recommend migrating to misk.metrics.v2.Metrics.histogram. See kdoc for detail",
    level = DeprecationLevel.WARNING,
  )
  override fun legacyHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ) = super.legacyHistogram(name, help, labelNames, quantiles, maxAgeSeconds)

  @Deprecated(
    message = "Recommend migrating to misk.metrics.v2.Metrics.histogram. See kdoc for detail",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("legacyHistogram(name,help,labelNames,quantiles,maxAgeSeconds)"),
  )
  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ) = super.histogram(name, help, labelNames, quantiles, maxAgeSeconds)

  /** Returns a measurement for a [counter] or [gauge]. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun get(name: String, vararg labels: Pair<String, String>): Double? = registry.get(name, *labels)

  /** Returns the number of histogram samples taken. */
  @Deprecated("Use summaryCount extention method on CollectorRegistry instead")
  fun histogramCount(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryCount(name, *labels)

  /** Returns the sum of all histogram samples taken. */
  @Deprecated("Use summarySum extention method on CollectorRegistry instead")
  fun histogramSum(name: String, vararg labels: Pair<String, String>): Double? = registry.summarySum(name, *labels)

  /** Returns the average of all histogram samples taken. */
  @Deprecated("Use summaryMean extention method on CollectorRegistry instead")
  fun histogramMean(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryMean(name, *labels)

  /** Returns the median for a [histogram]. In small samples this is the element preceding the middle element. */
  @Deprecated("Use summaryP50 extention method on CollectorRegistry instead")
  fun histogramP50(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryP50(name, *labels)

  /** Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest element. */
  @Deprecated("Use summaryP99 extention method on CollectorRegistry instead")
  fun histogramP99(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryP99(name, *labels)

  @Deprecated("Use summaryQuantile extention method on CollectorRegistry instead")
  fun histogramQuantile(name: String, quantile: String, vararg labels: Pair<String, String>) =
    registry.summaryQuantile(name, quantile, *labels)

  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun getSample(name: String, labels: Array<out Pair<String, String>>, sampleName: String? = null) =
    registry.getSample(name, labels, sampleName)

  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun getAllSamples(): Sequence<Sample> = registry.getAllSamples()
}

/** Returns a measurement for a [counter] or [gauge]. */
fun CollectorRegistry.get(name: String, vararg labels: Pair<String, String>): Double? = getSample(name, labels)?.value

/** Returns the number of histogram samples taken. */
fun CollectorRegistry.summaryCount(name: String, vararg labels: Pair<String, String>): Double? =
  getSample(name, labels, sampleName = name + "_count")?.value

/** Returns the sum of all histogram samples taken. */
fun CollectorRegistry.summarySum(name: String, vararg labels: Pair<String, String>): Double? =
  getSample(name, labels, sampleName = name + "_sum")?.value

/** Returns the average of all histogram samples taken. */
fun CollectorRegistry.summaryMean(name: String, vararg labels: Pair<String, String>): Double? {
  val sum = summarySum(name, *labels) ?: return null
  val count = summaryCount(name, *labels) ?: return null
  return sum / count
}

/** Returns the median for a [histogram]. In small samples this is the element preceding the middle element. */
fun CollectorRegistry.summaryP50(name: String, vararg labels: Pair<String, String>): Double? =
  summaryQuantile(name, "0.5", *labels)

/** Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest element. */
fun CollectorRegistry.summaryP99(name: String, vararg labels: Pair<String, String>): Double? =
  summaryQuantile(name, "0.99", *labels)

fun CollectorRegistry.summaryQuantile(name: String, quantile: String, vararg labels: Pair<String, String>): Double? {
  val extraLabels = labels.toList() + ("quantile" to quantile)
  return getSample(name, extraLabels.toTypedArray())?.value
}

fun CollectorRegistry.getSample(
  name: String,
  labels: Array<out Pair<String, String>>,
  sampleName: String? = null,
): Sample? {

  val metricFamilySamples =
    this.metricFamilySamples().asSequence().firstOrNull {
      it.name == name || (it.type == Collector.Type.COUNTER && "${it.name}_total" == name)
    } ?: return null

  val familySampleName =
    sampleName
      ?: if (metricFamilySamples.type == Collector.Type.COUNTER && !name.endsWith("_total")) {
        "${name}_total"
      } else {
        name
      }

  val labelNames = labels.map { it.first }
  val labelValues = labels.map { it.second }

  return metricFamilySamples.samples.firstOrNull {
    it.name == familySampleName && it.labelNames == labelNames && it.labelValues == labelValues
  }
}

fun CollectorRegistry.getAllSamples(): Sequence<Sample> = this.metricFamilySamples().asSequence().flatMap { it.samples }
