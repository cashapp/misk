package misk.metrics

import io.prometheus.client.CollectorRegistry

open class MetricsScope internal constructor(
  private val registry: CollectorRegistry,
  internal val labels: Map<String, String>
)
