package misk.prometheus

import misk.metrics.HistogramRegistry
import misk.metrics.Metrics
import jakarta.inject.Inject

@Deprecated("use Metrics instead")
class PrometheusHistogramRegistry @Inject constructor(
  val metrics: Metrics
) : HistogramRegistry(metrics)
