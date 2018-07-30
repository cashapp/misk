package misk.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary

open class MetricsScope internal constructor(
  private val registry: CollectorRegistry,
  internal val labels: Map<String, String>
) {
}
