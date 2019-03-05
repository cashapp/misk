package misk.prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Summary
import misk.metrics.HistogramRegistry
import javax.inject.Inject

class PrometheusHistogramRegistry @Inject constructor(): HistogramRegistry {
  @Inject lateinit var collectorRegistry: CollectorRegistry

  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): PrometheusHistogram {
    val builder = Summary.build(name, help).labelNames(*labelNames.toTypedArray())
    quantiles.forEach { quantile ->
      builder.quantile(quantile.key, quantile.value)
    }
    return PrometheusHistogram(builder.register(collectorRegistry))
  }
}