package misk.prometheus

import io.prometheus.client.CollectorRegistry
import misk.metrics.HistogramRegistry
import javax.inject.Inject

class PrometheusHistogramRegistry : HistogramRegistry {
  @Inject lateinit var collectorRegistry: CollectorRegistry

  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: DoubleArray?
  ): PrometheusHistogram {
    val builder =
        io.prometheus.client.Histogram.build(name, help).labelNames(*labelNames.toTypedArray())
    if (buckets != null) builder.buckets(*buckets)
    return PrometheusHistogram(builder.register(collectorRegistry))
  }
}