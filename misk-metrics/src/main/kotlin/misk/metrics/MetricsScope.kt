package misk.metrics

import io.prometheus.client.CollectorRegistry

@Deprecated("Unexpected that this class is used.")
open class MetricsScope internal constructor(
  private val registry: CollectorRegistry,
  internal val labels: Map<String, String>
)
