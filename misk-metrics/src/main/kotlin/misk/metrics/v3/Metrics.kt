package misk.metrics.v3

import com.google.common.util.concurrent.AtomicDouble
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag

import jakarta.inject.Inject
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Convenience methods to simplify the use of the micrometer metrics backend
 *
 * NOTE: `misk.metrics.v3.Metrics` is NOT backward compatible with `misk.metrics.Metrics` or `misk.metrics.v2.Metrics`.
 *   This is a major overhaul of the api to use Micrometer instead of Prometheus.
 *
 * Tests that use this should install a metrics client like `PrometheusMetricsClientModule`.
 * Services that use this should install a metrics service like `PrometheusMetricsServiceModule`.
 */
open class Metrics @Inject internal constructor(
  private val registry: MeterRegistry
) {
  /**
   * counter creates and registers a new `Counter` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Counter.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param description a human-readable description of what the metric does for display in the metrics UI.
   * @param tags a list of key/value pairs to categorize the metric.
   */
  @JvmOverloads
  fun counter(
    name: String,
    description: String,
    tags: Iterable<Tag> = listOf(),
  ) = Counter
    .builder(name)
    .description(description)
    .tags(tags)
    .register(registry)

  /**
   * gauge creates and registers a new `Gauge` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Gauge.html for more info.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param supplier a closure that will be called when rendering metrics for the metrics backend.
   * @param description a human-readable description of what the metric does for display in the metrics UI.
   * @param tags a list of key/value pairs to categorize the metric.
   */
  @JvmOverloads
  fun gauge(
    name: String,
    supplier: Supplier<Number>,
    description: String = "",
    tags: Iterable<Tag> = listOf(),
  ) = Gauge
    .builder(name, supplier)
    .description(description)
    .tags(tags)
    .register(registry)

  @JvmOverloads
  fun peakGauge(
    name: String,
    description: String = "",
    tags: Iterable<Tag> = listOf(),
  ): PeakGauge {
    // For simplicity, using an atomic double with an initial value of 0 here.
    //
    // If we cared to differentiate between the initial value due to no samples vs having
    // samples that are equal or less than 0, then we would need to also track whether we
    // have received an update since the last reset.
    val value = AtomicDouble()
    val gauge = Gauge.builder(name) { value.getAndSet(0.0) }
      .description(description)
      .tags(tags)
      .register(registry)
    return PeakGauge(gauge, value)
  }

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
   * @param description a human-readable description of what the metric does for display in the metrics UI.
   * @param tags a list of key/value pairs to categorize the metric.
   * @param buckets a list of upper bounds of buckets for the histogram.
   */
  @JvmOverloads
  fun histogram(
    name: String,
    description: String = "",
    tags: Iterable<Tag> = listOf(),
    buckets: List<Double> = defaultBuckets
  ) = DistributionSummary.builder(name)
    .description(description)
    .tags(tags)
    .serviceLevelObjectives(*buckets.toDoubleArray())
    .register(registry)

  /**
   * summary creates and registers a new `Summary` prometheus type.
   *
   * See https://prometheus.github.io/client_java/io/prometheus/client/Summary.html for more info.
   *
   * NB: Summaries can be an order of magnitude more expensive than histograms in terms of CPU.
   * Unless you require the specific properties of a summary, consider using [histogram] instead.
   *
   * @param name the name of the metric which will be supplied to prometheus.
   *  Must be unique across all metric types.
   * @param description a human-readable description of what the metric does for display in the metrics UI.
   * @param tags a list of key/value pairs to categorize the metric.
   * @param quantiles is a map of all of the quantiles (a.k.a. percentiles) that will be computed
   *  for the metric. The key of the map is the quantile as a ratio (e.g. 0.99 represents p99) and
   *  the value is the "tolerable error" of the computed quantile.
   */
  @JvmOverloads
  fun summary(
    name: String,
    description: String = "",
    tags: Iterable<Tag> = listOf(),
    quantiles: Map<Double, Double> = defaultQuantiles,
    maxAgeSeconds: Long = TimeUnit.MINUTES.toMillis(10)
  ): DistributionSummary {

    val precision = quantiles.values.maxOfOrNull { it.toString().substringAfter('.').length } ?: 4
    return DistributionSummary.builder(name)
      .description(description)
      .tags(tags)
      .publishPercentiles(*quantiles.keys.toDoubleArray())

      .percentilePrecision(precision)
      // The default for this in prometheus was 10 minutes (above), default in micrometer is 2 minutes
      .distributionStatisticExpiry(Duration.ofMillis(maxAgeSeconds))
      // this is the default in prometheus. The default in micrometer is 3
      .distributionStatisticBufferLength(5)
      .register(registry)
  }

  /**
   * A peak gauge is a wrapper around an AtomicDouble that resets to an initial value of 0 after
   * observation (i.e. after a metric collection.)
   *
   * This is useful for accurately capturing maximum observed values over time. In contrast to the
   * histogram maximum which tracks the maximum value in its sampling window. That sampling window
   * typically covers multiple metric collections.
   */
  class PeakGauge(
    private val gauge: Gauge,
    private val value: AtomicDouble,
  ) : Gauge by gauge {

    /**
     * Updates the stored value if the new value is greater.
     */
    fun record(newValue: Double) {
      while (true) {
        val previous = value.get()
        if (newValue > previous) {
          value.compareAndSet(previous, newValue)
        } else {
          return
        }
      }
    }
  }
}

val defaultQuantiles = mapOf(
  0.5 to 0.05,
  0.75 to 0.02,
  0.95 to 0.01,
  0.99 to 0.001,
  0.999 to 0.0001
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
