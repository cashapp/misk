package misk.prometheus

import misk.metrics.HistogramRegistry
import misk.metrics.Metrics
import jakarta.inject.Inject

@Deprecated("use Metrics instead")
class PrometheusHistogramRegistry @Inject constructor(
  private val metrics: Metrics
) : HistogramRegistry {
  override final fun getMetrics() = metrics

  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ) = super.newHistogram(name, help, labelNames, quantiles)
}
