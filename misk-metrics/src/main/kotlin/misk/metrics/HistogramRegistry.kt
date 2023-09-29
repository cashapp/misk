package misk.metrics

/**
 * Skeleton to create a new histogram.
 *
 * Implementation should register the histogram upon creation.
 * An example implementation can be found in PrometheusHistogramRegistry
 */
@Deprecated("use Metrics.histogram() instead")
interface HistogramRegistry {

  fun getMetrics(): Metrics

  /** Creates a new histogram */
  fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): Histogram {
    return getMetrics().histogram(name, help, labelNames, quantiles)
  }

  companion object {
    fun factory(metrics: Metrics) = object : HistogramRegistry {
      override fun getMetrics() = metrics
    }
  }
}
