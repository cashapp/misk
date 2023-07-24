package misk.prometheus

import misk.metrics.Histogram
import misk.metrics.HistogramRegistry
import misk.metrics.Metrics
import com.google.inject.Inject

@Deprecated("use Metrics instead")
class PrometheusHistogramRegistry @Inject constructor(
  val metrics: Metrics
) : HistogramRegistry {
  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Histogram {
    return metrics.histogram(name, help, labelNames, quantiles)
  }
}
