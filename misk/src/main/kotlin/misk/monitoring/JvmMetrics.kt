package misk.monitoring

import io.prometheus.client.Gauge
import misk.metrics.v2.Metrics
import java.lang.management.RuntimeMXBean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes additional JVM metrics.
 */
@Singleton
class JvmMetrics @Inject constructor(
  private val runtimeMxBean: RuntimeMXBean,
  metrics: Metrics,
) {
  /**
   * Exposes the JVM uptime in milliseconds as a gauge with a custom no-labels child
   * that retrieves the current time when the Gauge is read
   *
   * Uptime is useful for a few things:
   * - Allows for easy correlation of other metrics with process startup (e.g. latencies
   *   might be slower early in a fresh VM without warm caches, pools, or full JIT)
   * - Allows for the correlation of elapsed time against the resulting time-series. This
   *   can be a useful operational tool to help reason about artifacts from time and space
   *   aggregation in a metrics pipeline (e.g. we know that 1000ms _should_ be the observed
   *   rate of time elapsed per second).
   *
   * (Normal gauges have their values explicitly set,  but in the case of uptime we just
   * want the latest time whenever metrics are scraped to get the most accurate time)
   */
  @Suppress("unused") // Once registered, the metrics system will poll this.
  private val uptime: Gauge =
    metrics.gauge("jvm_uptime_ms", "JVM uptime in milliseconds", listOf())
      .setChild(object : Gauge.Child() {
        override fun get(): Double {
          return runtimeMxBean.uptime.toDouble()
        }
      })
}
