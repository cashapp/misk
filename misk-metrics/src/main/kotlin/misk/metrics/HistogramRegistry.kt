package misk.metrics

/**
 * Skeleton to create a new histogram.
 *
 * Implementation should register the histogram upon creation.
 * An example implementation can be found in PrometheusHistogramRegistry
 */
@Deprecated("use Metrics.histogram() instead")
open class HistogramRegistry(
  private val metrics: Metrics,
  ) {
  /** Creates a new histogram */
   fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Histogram {
    return metrics.histogram(name, help, labelNames, quantiles)
  }
}
