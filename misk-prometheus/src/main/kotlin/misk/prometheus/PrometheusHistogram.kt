package misk.prometheus

import io.prometheus.client.Summary
import misk.metrics.Histogram

@Deprecated("Unexpected that this is used. Checkout Metrics instead")
class PrometheusHistogram(
  private val histogram: Summary
) : Histogram {
  override final fun getHistogram() = histogram
}
