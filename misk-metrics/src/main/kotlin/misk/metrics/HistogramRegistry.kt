package misk.metrics

/**
 * Skeleton to create a new histogram.
 *
 * Implementation should register the histogram upon creation.
 * An example implementation can be found in PrometheusHistogramRegistry
 */
@Deprecated("use Metrics.histogram() instead")
interface HistogramRegistry {
  /** Creates a new histogram */
  fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Histogram
}
