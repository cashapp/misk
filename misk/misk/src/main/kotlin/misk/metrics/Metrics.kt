package misk.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.hotspot.BufferPoolsExports
import io.prometheus.client.hotspot.ClassLoadingExports
import io.prometheus.client.hotspot.GarbageCollectorExports
import io.prometheus.client.hotspot.MemoryPoolsExports
import io.prometheus.client.hotspot.StandardExports
import io.prometheus.client.hotspot.ThreadExports
import io.prometheus.client.hotspot.VersionInfoExports
import javax.inject.Inject

class Metrics @Inject internal constructor(val registry: CollectorRegistry) {
  @Inject private lateinit var histogramRegistry: HistogramRegistry

  private val defaultQuantiles = mapOf(0.5 to 0.05, 0.75 to 0.02, 0.95 to 0.01, 0.99 to 0.001, 0.999 to 0.0001)

  init {
    registry.register(StandardExports())
    registry.register(MemoryPoolsExports())
    registry.register(BufferPoolsExports())
    registry.register(ThreadExports())
    registry.register(GarbageCollectorExports())
    registry.register(ClassLoadingExports())
    registry.register(VersionInfoExports())
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
