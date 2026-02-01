package misk.micrometer.prometheus.v2

import io.micrometer.core.instrument.MeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import misk.metrics.v2.Metrics
import misk.metrics.v2.PeakGauge
import misk.metrics.v2.ProvidedGauge

class MicrometerMetrics(private val meterRegistry: MeterRegistry, private val collectorRegistry: CollectorRegistry) :
  Metrics {
  override fun getRegistry(): CollectorRegistry = collectorRegistry

  override fun counter(name: String, help: String, labelNames: List<String>): Counter {
    meterRegistry.counter(name)
    return Counter.build(name, help).labelNames(*labelNames.toTypedArray()).register(collectorRegistry)
  }

  override fun gauge(name: String, help: String, labelNames: List<String>): Gauge {
    return Gauge.build(name, help).labelNames(*labelNames.toTypedArray()).register(collectorRegistry)
  }

  override fun peakGauge(name: String, help: String, labelNames: List<String>): PeakGauge {
    return PeakGauge.builder(name, help).labelNames(*labelNames.toTypedArray()).register(collectorRegistry)
  }

  override fun providedGauge(name: String, help: String, labelNames: List<String>): ProvidedGauge {
    return ProvidedGauge.builder(name, help).labelNames(*labelNames.toTypedArray()).register(collectorRegistry)
  }

  override fun histogram(name: String, help: String, labelNames: List<String>, buckets: List<Double>): Histogram {
    io.micrometer.core.instrument.Timer.builder(name)
      .publishPercentileHistogram()
      .serviceLevelObjectives(*buckets.map { java.time.Duration.ofMillis(it.toLong()) }.toTypedArray())
      .register(meterRegistry)

    return Histogram.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .buckets(*buckets.toDoubleArray())
      .register(collectorRegistry)
  }

  override fun summary(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ): Summary {
    return Summary.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .apply { quantiles.forEach { (key, value) -> quantile(key, value) } }
      .apply {
        if (maxAgeSeconds != null) {
          this.maxAgeSeconds(maxAgeSeconds)
        }
      }
      .register(collectorRegistry)
  }
}
