package misk.metrics.backends.prometheus

import io.prometheus.client.Summary as PrometheusSummary
import misk.metrics.Histogram

/**
 * PrometheusHistogram implements `Histogram` interface with prometheus `Summary` type.
 */
internal class PrometheusHistogram constructor(
  val histogram: PrometheusSummary
) : Histogram {
  override fun record(duration: Double, vararg labelValues: String) {
    histogram.labels(*labelValues).observe(duration)
  }

  override fun count(vararg labelValues: String): Int {
    return histogram.labels(*labelValues).get().count.toInt()
  }
}
