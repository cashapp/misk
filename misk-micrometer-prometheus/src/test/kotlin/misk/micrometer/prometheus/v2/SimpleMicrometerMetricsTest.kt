package misk.micrometer.prometheus.v2

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.prometheus.client.CollectorRegistry
import misk.metrics.v2.Metrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SimpleMicrometerMetricsTest {
  private val collectorRegistry = CollectorRegistry()
  private val meterRegistry = SimpleMeterRegistry()
  private val metrics: Metrics = MicrometerMetrics(meterRegistry, collectorRegistry)

  @Test
  fun `counter increments properly`() {
    val counter = metrics.counter("test_counter", "Test counter", listOf("label1"))
    counter.labels("value1").inc()
    counter.labels("value1").inc(2.0)

    assertThat(counter.labels("value1").get()).isEqualTo(3.0)
  }

  @Test
  fun `gauge sets values properly`() {
    val gauge = metrics.gauge("test_gauge", "Test gauge", listOf("label1"))
    gauge.labels("value1").set(42.0)

    assertThat(gauge.labels("value1").get()).isEqualTo(42.0)

    gauge.labels("value1").inc()
    assertThat(gauge.labels("value1").get()).isEqualTo(43.0)

    gauge.labels("value1").dec(3.0)
    assertThat(gauge.labels("value1").get()).isEqualTo(40.0)
  }

  @Test
  fun `histogram records observations`() {
    val histogram = metrics.histogram("test_histogram", "Test histogram", listOf("label1"), listOf(1.0, 5.0, 10.0))

    histogram.labels("value1").observe(0.5)
    histogram.labels("value1").observe(3.0)
    histogram.labels("value1").observe(7.0)
    histogram.labels("value1").observe(15.0)

    val samples = collectorRegistry.metricFamilySamples()
    val histogramFamily = samples.asSequence().find { it.name == "test_histogram" }
    assertThat(histogramFamily).isNotNull
  }

  @Test
  fun `metrics are registered in collector registry`() {
    metrics.counter("registered_counter", "Test")
    metrics.gauge("registered_gauge", "Test")

    val metricNames = collectorRegistry.metricFamilySamples().asSequence().map { it.name }.toList()

    assertThat(metricNames).contains("registered_counter", "registered_gauge")
  }
}
