package misk.prometheus

import io.prometheus.client.Summary
import misk.metrics.Histogram
import misk.metrics.HistogramRecordMetric

class PrometheusHistogram constructor(
  val histogram: io.prometheus.client.Summary
) : Histogram {

  override fun record(vararg labelValues: String): HistogramRecordMetric {
    return PrometheusHistogramRecordMetric(histogram.labels(*labelValues))
  }

  override fun count(vararg labelValues: String): Int {
    return histogram.labels(*labelValues).get().count.toInt()
  }
}

class PrometheusHistogramRecordMetric constructor(private val child: Summary.Child) :
    HistogramRecordMetric {
  override fun observe(duration: Double) {
    child.observe(duration)
  }
}