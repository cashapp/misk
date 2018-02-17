package misk.metrics

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import java.util.SortedMap
import javax.inject.Inject

class Metrics @Inject internal constructor(
    metricRegistry: MetricRegistry
) : MetricsScope("", metricRegistry) {
  val timers: SortedMap<String, Timer> get() = metricRegistry.timers
  val counters: SortedMap<String, Counter> get() = metricRegistry.counters
  val gauges: SortedMap<String, Gauge<Any>> get() = metricRegistry.gauges
  val histograms: SortedMap<String, Histogram> get() = metricRegistry.histograms

  companion object {
    /**
     * @return a version of the name, sanitized to remove elements that are incompatible
     * with the major metrics collection systems (notably graphite)
     */
    fun sanitize(name: String) = name.replace("[\\-\\.\t]", "_")
  }
}
