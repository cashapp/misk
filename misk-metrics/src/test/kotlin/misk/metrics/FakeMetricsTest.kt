package misk.metrics

import io.prometheus.client.Collector.MetricFamilySamples.Sample
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class FakeMetricsTest {
  @MiskTestModule val module = FakeMetricsModule()

  @Inject lateinit var metrics: Metrics
  @Inject lateinit var registry: CollectorRegistry

  @Test
  internal fun `count happy path`() {
    assertThat(registry.get("gets", "status" to "200")).isNull()
    val counter = metrics.counter("gets", "-", labelNames = listOf("status")).labels("200")
    counter.inc()
    assertThat(registry.get("gets", "status" to "200")).isEqualTo(1.0)
    counter.inc()
    assertThat(registry.get("gets", "status" to "200")).isEqualTo(2.0)
  }

  @Test
  internal fun `gauge happy path`() {
    assertThat(registry.get("thread_count", "state" to "running")).isNull()
    val gauge = metrics.gauge("thread_count", "-", labelNames = listOf("state")).labels("running")
    gauge.set(20.0)
    assertThat(registry.get("thread_count", "state" to "running")).isEqualTo(20.0)
    gauge.set(30.0)
    assertThat(registry.get("thread_count", "state" to "running")).isEqualTo(30.0)
  }

  @Test
  internal fun `histogram happy path`() {
    assertThat(registry.get("call_times", "status" to "200")).isNull()
    val histogram = metrics.histogram("call_times", "-", labelNames = listOf("status"))
    histogram.record(100.0, "200")
    assertThat(registry.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(registry.summarySum("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(registry.summaryCount("call_times", "status" to "200")).isEqualTo(1.0)
    assertThat(registry.summaryP50("call_times", "status" to "200")).isEqualTo(100.0)
    histogram.record(99.0, "200")
    histogram.record(101.0, "200")
    assertThat(registry.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(registry.summarySum("call_times", "status" to "200")).isEqualTo(300.0)
    assertThat(registry.summaryCount("call_times", "status" to "200")).isEqualTo(3.0)
    assertThat(registry.summaryP50("call_times", "status" to "200")).isIn(99.0, 100.0, 101.0)
  }

  @Test
  internal fun `different label values`() {
    assertThat(registry.get("gets", "status" to "200")).isNull()
    assertThat(registry.get("gets", "status" to "503")).isNull()

    val counter = metrics.counter("gets", "-", labelNames = listOf("status"))
    val counter200s = counter.labels("200")
    val counter503s = counter.labels("503")

    counter200s.inc(7.0)
    counter503s.inc(9.0)
    assertThat(registry.get("gets", "status" to "200")).isEqualTo(7.0)
    assertThat(registry.get("gets", "status" to "503")).isEqualTo(9.0)

    counter200s.inc(10.0)
    counter503s.inc(20.0)
    assertThat(registry.get("gets", "status" to "200")).isEqualTo(17.0)
    assertThat(registry.get("gets", "status" to "503")).isEqualTo(29.0)
  }

  @Test
  internal fun `different names`() {
    assertThat(registry.get("gets")).isNull()
    assertThat(registry.get("puts")).isNull()
    assertThat(registry.get("gets_total")).isNull()
    assertThat(registry.get("puts_total")).isNull()

    val getsCounter = metrics.counter("gets", "-")
    val putsCounter = metrics.counter("puts", "-")

    getsCounter.inc(7.0)
    putsCounter.inc(9.0)
    assertThat(registry.get("gets")).isEqualTo(7.0)
    assertThat(registry.get("puts")).isEqualTo(9.0)

    getsCounter.inc(10.0)
    putsCounter.inc(20.0)
    assertThat(registry.get("gets")).isEqualTo(17.0)
    assertThat(registry.get("puts")).isEqualTo(29.0)
    assertThat(registry.get("gets_total")).isEqualTo(17.0)
    assertThat(registry.get("puts_total")).isEqualTo(29.0)
  }

  @Test
  internal fun `histogram quantiles`() {
    val histogram = metrics.legacyHistogram("call_times", "-")

    histogram.record(400.0)
    assertThat(registry.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(registry.summaryP99("call_times")).isEqualTo(400.0)

    histogram.record(450.0)
    assertThat(registry.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(registry.summaryP99("call_times")).isEqualTo(450.0)

    histogram.record(500.0)
    assertThat(registry.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(registry.summaryP99("call_times")).isEqualTo(500.0)

    histogram.record(550.0)
    assertThat(registry.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(registry.summaryP99("call_times")).isEqualTo(550.0)

    histogram.record(600.0)
    assertThat(registry.summaryP50("call_times")).isEqualTo(500.0)
    assertThat(registry.summaryP99("call_times")).isEqualTo(600.0)
  }

  @Test
  internal fun `get all samples`() {
    metrics.counter("counter_total", "-", listOf("foo")).labels("bar").inc()
    metrics.gauge("gauge", "-", listOf("foo")).labels("bar").inc()
    val quantiles = mapOf(0.5 to 0.5, 0.99 to 0.99)
    metrics.legacyHistogram("histogram", "-", listOf("foo"), quantiles).record(1.0, "bar")

    assertThat(registry.getAllSamples().toList())
      .contains(
        Sample("counter_total", listOf("foo"), listOf("bar"), 1.0),
        Sample("gauge", listOf("foo"), listOf("bar"), 1.0),
        Sample("histogram", listOf("foo", "quantile"), listOf("bar", "0.5"), 1.0),
        Sample("histogram", listOf("foo", "quantile"), listOf("bar", "0.99"), 1.0),
        Sample("histogram_count", listOf("foo"), listOf("bar"), 1.0),
        Sample("histogram_sum", listOf("foo"), listOf("bar"), 1.0),
      )
  }
}
