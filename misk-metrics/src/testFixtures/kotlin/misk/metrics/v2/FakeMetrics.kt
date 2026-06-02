package misk.metrics.v2

import io.prometheus.client.Collector.MetricFamilySamples.Sample
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.get
import misk.metrics.getAllSamples
import misk.metrics.getSample
import misk.metrics.summaryCount
import misk.metrics.summaryMean
import misk.metrics.summaryP50
import misk.metrics.summaryP99
import misk.metrics.summaryQuantile
import misk.metrics.summarySum

/**
 * An in-memory metrics store with APIs to verify which metrics were collected.
 *
 * The only way to create an instance of this is with [FakeMetricsModule].
 */
@Singleton
class FakeMetrics @Inject internal constructor(private val registry: CollectorRegistry) : Metrics {
  override fun getRegistry() = registry

  override fun counter(name: String, help: String, labelNames: List<String>) = super.counter(name, help, labelNames)

  override fun gauge(name: String, help: String, labelNames: List<String>) = super.gauge(name, help, labelNames)

  override fun peakGauge(name: String, help: String, labelNames: List<String>) = super.peakGauge(name, help, labelNames)

  override fun providedGauge(name: String, help: String, labelNames: List<String>) =
    super.providedGauge(name, help, labelNames)

  override fun histogram(name: String, help: String, labelNames: List<String>, buckets: List<Double>) =
    super.histogram(name, help, labelNames, buckets)

  override fun summary(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ) = super.summary(name, help, labelNames, quantiles, maxAgeSeconds)

  @Deprecated("Recommend migrating to histogram. See kdoc for detail", level = DeprecationLevel.WARNING)
  override fun legacyHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ) = super.legacyHistogram(name, help, labelNames, quantiles, maxAgeSeconds)

  /** Returns a measurement for a [counter] or [gauge]. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun get(name: String, vararg labels: Pair<String, String>): Double? = registry.get(name, *labels)

  /** Returns the number of histogram samples taken. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun summaryCount(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryCount(name, *labels)

  /** Returns the sum of all histogram samples taken. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun summarySum(name: String, vararg labels: Pair<String, String>): Double? = registry.summarySum(name, *labels)

  /** Returns the average of all histogram samples taken. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun summaryMean(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryMean(name, *labels)

  /** Returns the median for a [histogram]. In small samples this is the element preceding the middle element. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun summaryP50(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryP50(name, *labels)

  /** Returns the 0.99th percentile for a [histogram]. In small samples this is the second largest element. */
  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun summaryP99(name: String, vararg labels: Pair<String, String>): Double? = registry.summaryP99(name, *labels)

  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun summaryQuantile(name: String, quantile: String, vararg labels: Pair<String, String>) =
    registry.summaryQuantile(name, quantile, *labels)

  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun getSample(name: String, labels: Array<out Pair<String, String>>, sampleName: String? = null) =
    registry.getSample(name, labels, sampleName)

  @Deprecated("Use same extention method on CollectorRegistry instead")
  fun getAllSamples(): Sequence<Sample> = registry.getAllSamples()
}
