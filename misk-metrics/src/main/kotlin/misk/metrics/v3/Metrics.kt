package misk.metrics.v3

import io.micrometer.core.instrument.MeterRegistry

val defaultBuckets = misk.metrics.v2.defaultBuckets
val defaultSparseBuckets = misk.metrics.v2.defaultSparseBuckets
val defaultQuantiles = misk.metrics.v2.defaultQuantiles

/**
 * Interface for application code to emit metrics to a metrics backend like Prometheus via Micrometer.
 *
 * v3.Metrics wraps Micrometer's MeterRegistry to provide a familiar Misk-style API while delegating to Micrometer's
 * metrics implementation. This allows using Micrometer's richer ecosystem while maintaining API compatibility with
 * v2.Metrics.
 *
 * Tests that use this should install [MetricsV3Module] along with [MicrometerModule] and [MicrometerPrometheusModule].
 * Services that use this should install the same modules.
 */
interface Metrics {
  fun registry(): MeterRegistry

  /**
   * counter creates and registers a new Counter metric.
   *
   * @param name the name of the metric. Must be unique across all metric types.
   * @param help human-readable help text.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  fun counter(name: String, help: String = "", labelNames: List<String> = listOf()): Counter

  /**
   * gauge creates and registers a new Gauge metric.
   *
   * @param name the name of the metric. Must be unique across all metric types.
   * @param help human-readable help text.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  fun gauge(name: String, help: String = "", labelNames: List<String> = listOf()): Gauge

  /**
   * peakGauge creates and registers a new Gauge metric that resets to 0 after each read.
   *
   * @param name the name of the metric. Must be unique across all metric types.
   * @param help human-readable help text.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  fun peakGauge(name: String, help: String = "", labelNames: List<String> = listOf()): Gauge

  /**
   * providedGauge creates and registers a new Gauge metric that gets its value from a supplier.
   *
   * @param name the name of the metric. Must be unique across all metric types.
   * @param help human-readable help text.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   */
  fun providedGauge(name: String, help: String = "", labelNames: List<String> = listOf()): ProvidedGauge

  /**
   * histogram creates and registers a new Histogram metric with the supplied buckets.
   *
   * @param name the name of the metric. Must be unique across all metric types.
   * @param help human-readable help text.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param bucketsMs a list of upper bounds of buckets for the histogram in milliseconds.
   */
  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    bucketsMs: List<Double> = defaultBuckets,
  ): Histogram

  /**
   * summary creates and registers a new Summary metric.
   *
   * @param name the name of the metric. Must be unique across all metric types.
   * @param help human-readable help text.
   * @param labelNames the names (a.k.a. keys) of all the labels that will be used for this metric.
   * @param quantiles is a map of all of the quantiles (a.k.a. percentiles) that will be computed for the metric.
   * @param maxAgeSeconds maximum age of samples before they are discarded.
   */
  fun summary(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    quantiles: Map<Double, Double> = defaultQuantiles,
    maxAgeSeconds: Long? = null,
  ): Summary

  interface Counter {
    fun labels(vararg labelValues: String): Child

    interface Child {
      fun inc(amount: Double = 1.0)
    }
  }

  interface Gauge {
    fun labels(vararg labelValues: String): Child

    interface Child {
      fun set(value: Double)

      fun inc(amount: Double = 1.0)

      fun dec(amount: Double = 1.0) = inc(-amount)

      fun get(): Double
    }
  }

  interface ProvidedGauge {
    fun labels(vararg labelValues: String): Child

    interface Child {
      fun setSupplier(supplier: () -> Double)
    }
  }

  interface Histogram {
    fun labels(vararg labelValues: String): Child

    interface Child {
      fun observe(value: Double)

      fun timeMs(block: () -> Unit): Double
    }
  }

  interface Summary {
    fun labels(vararg labelValues: String): Child

    interface Child {
      fun observe(value: Double)
    }
  }

  companion object {
    fun from(registry: MeterRegistry): Metrics = MetricsImpl(registry)
  }
}
