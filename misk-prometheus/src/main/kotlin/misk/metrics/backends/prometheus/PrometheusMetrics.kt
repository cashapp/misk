package misk.metrics.backends.prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import misk.metrics.Histogram
import misk.metrics.Metrics
import misk.prometheus.PrometheusHistogram
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accepts metrics and writes them to the Prometheus [CollectorRegistry].
 */
@Singleton
internal class PrometheusMetrics @Inject internal constructor(
  private val registry: CollectorRegistry
) : Metrics {
  override fun counter(name: String, help: String?, labelNames: List<String>): Counter {
    return Counter.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)
  }

  override fun gauge(name: String, help: String, labelNames: List<String>): Gauge {
    return Gauge.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)
  }

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Histogram {
    val builder = Summary.build(name, help).labelNames(*labelNames.toTypedArray())
    quantiles.forEach { quantile ->
      builder.quantile(quantile.key, quantile.value)
    }
    return PrometheusHistogram(builder.register(registry))
  }

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
