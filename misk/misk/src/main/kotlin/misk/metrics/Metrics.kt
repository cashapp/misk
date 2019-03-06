package misk.metrics

import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import javax.inject.Inject

/**
 * Manages the Prometheus [CollectorRegistry].
 *
 * Developers can either directly build new metrics via the [counter], [gauge] and [histogram]
 * methods or add additional [Collector]s by binding to the multiset in a module.
 *
 * ```
 * multibind<Collector>().toInstance(MyCollector())
 * ```
 */
class Metrics @Inject internal constructor(
  val registry: CollectorRegistry,
  collectors: @JvmSuppressWildcards Set<Collector>
) {
  @Inject private lateinit var histogramRegistry: HistogramRegistry

  private val defaultQuantiles = mapOf(0.5 to 0.05, 0.75 to 0.02, 0.95 to 0.01, 0.99 to 0.001, 0.999 to 0.0001)

  init {
    collectors.forEach { registry.register(it) }
  }

  fun counter(name: String, help: String? = "", labelNames: List<String> = listOf()): Counter {
    return Counter.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)
  }

  fun gauge(name: String, help: String = "", labelNames: List<String> = listOf()): Gauge {
    return Gauge.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)
  }

  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String>,
    quantiles: Map<Double, Double> = defaultQuantiles
  ): Histogram {
    return histogramRegistry.newHistogram(name, help, labelNames, quantiles)
  }

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
