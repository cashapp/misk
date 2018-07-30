package misk.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import io.prometheus.client.hotspot.BufferPoolsExports
import io.prometheus.client.hotspot.ClassLoadingExports
import io.prometheus.client.hotspot.GarbageCollectorExports
import io.prometheus.client.hotspot.MemoryPoolsExports
import io.prometheus.client.hotspot.StandardExports
import io.prometheus.client.hotspot.ThreadExports
import io.prometheus.client.hotspot.VersionInfoExports
import javax.inject.Inject

class Metrics @Inject internal constructor(val registry: CollectorRegistry) {
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

  fun summary(name: String, help: String = "", labelNames: List<String> = listOf()): Summary {
    return Summary.build(name, help).labelNames(*labelNames.toTypedArray()).register(registry)
  }

  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    buckets: DoubleArray? = null
  ): Histogram {
    val builder = Histogram.build(name, help).labelNames(*labelNames.toTypedArray())
    if (buckets != null) builder.buckets(*buckets)
    return builder.register(registry)
  }

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}

internal val Histogram.Child.Value.count get() = buckets.max()?.toInt() ?: 0
