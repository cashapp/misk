package misk.prometheus

import misk.metrics.Histogram

class PrometheusHistogram constructor(
  val histogram: io.prometheus.client.Summary
) : Histogram {

  override fun record(duration: Double, vararg labelValues: String) {
    histogram.labels(*labelValues).observe(duration)
  }

  override fun count(vararg labelValues: String): Int {
    return histogram.labels(*labelValues).get().count.toInt()
  }
}