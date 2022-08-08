package misk.metrics

import io.prometheus.client.Collector.MetricFamilySamples.Sample
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class FakeMetricsTest {
  @MiskTestModule val module = FakeMetricsModule()

  @Inject lateinit var metrics: FakeMetrics

  @Test
  internal fun `count happy path`() {
    assertThat(metrics.get("gets", "status" to "200")).isNull()
    val counter = metrics.counter("gets", "-", labelNames = listOf("status"))
      .labels("200")
    counter.inc()
    assertThat(metrics.get("gets", "status" to "200")).isEqualTo(1.0)
    counter.inc()
    assertThat(metrics.get("gets", "status" to "200")).isEqualTo(2.0)
  }

  @Test
  internal fun `gauge happy path`() {
    assertThat(metrics.get("thread_count", "state" to "running")).isNull()
    val gauge = metrics.gauge("thread_count", "-", labelNames = listOf("state"))
      .labels("running")
    gauge.set(20.0)
    assertThat(metrics.get("thread_count", "state" to "running")).isEqualTo(20.0)
    gauge.set(30.0)
    assertThat(metrics.get("thread_count", "state" to "running")).isEqualTo(30.0)
  }

  @Test
  internal fun `histogram happy path`() {
    assertThat(metrics.get("call_times", "status" to "200")).isNull()
    val histogram = metrics.histogram("call_times", "-", labelNames = listOf("status"))
    histogram.record(100.0, "200")
    assertThat(metrics.histogramMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(metrics.histogramSum("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(metrics.histogramCount("call_times", "status" to "200")).isEqualTo(1.0)
    assertThat(metrics.histogramP50("call_times", "status" to "200")).isEqualTo(100.0)
    histogram.record(99.0, "200")
    histogram.record(101.0, "200")
    assertThat(metrics.histogramMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(metrics.histogramSum("call_times", "status" to "200")).isEqualTo(300.0)
    assertThat(metrics.histogramCount("call_times", "status" to "200")).isEqualTo(3.0)
    assertThat(metrics.histogramP50("call_times", "status" to "200")).isIn(99.0, 100.0, 101.0)
  }

  @Test
  internal fun `different label values`() {
    assertThat(metrics.get("gets", "status" to "200")).isNull()
    assertThat(metrics.get("gets", "status" to "503")).isNull()

    val counter = metrics.counter("gets", "-", labelNames = listOf("status"))
    val counter200s = counter.labels("200")
    val counter503s = counter.labels("503")

    counter200s.inc(7.0)
    counter503s.inc(9.0)
    assertThat(metrics.get("gets", "status" to "200")).isEqualTo(7.0)
    assertThat(metrics.get("gets", "status" to "503")).isEqualTo(9.0)

    counter200s.inc(10.0)
    counter503s.inc(20.0)
    assertThat(metrics.get("gets", "status" to "200")).isEqualTo(17.0)
    assertThat(metrics.get("gets", "status" to "503")).isEqualTo(29.0)
  }

  @Test
  internal fun `different names`() {
    assertThat(metrics.get("gets")).isNull()
    assertThat(metrics.get("puts")).isNull()

    val getsCounter = metrics.counter("gets", "-")
    val putsCounter = metrics.counter("puts", "-")

    getsCounter.inc(7.0)
    putsCounter.inc(9.0)
    assertThat(metrics.get("gets")).isEqualTo(7.0)
    assertThat(metrics.get("puts")).isEqualTo(9.0)

    getsCounter.inc(10.0)
    putsCounter.inc(20.0)
    assertThat(metrics.get("gets")).isEqualTo(17.0)
    assertThat(metrics.get("puts")).isEqualTo(29.0)
  }

  @Test
  internal fun `histogram quantiles`() {
    val histogram = metrics.histogram("call_times", "-", labelNames = listOf())

    histogram.record(400.0)
    assertThat(metrics.histogramP50("call_times")).isEqualTo(400.0)
    assertThat(metrics.histogramP99("call_times")).isEqualTo(400.0)

    histogram.record(450.0)
    assertThat(metrics.histogramP50("call_times")).isEqualTo(400.0)
    assertThat(metrics.histogramP99("call_times")).isEqualTo(400.0)

    histogram.record(500.0)
    assertThat(metrics.histogramP50("call_times")).isEqualTo(400.0)
    assertThat(metrics.histogramP99("call_times")).isEqualTo(450.0)

    histogram.record(550.0)
    assertThat(metrics.histogramP50("call_times")).isEqualTo(450.0)
    assertThat(metrics.histogramP99("call_times")).isEqualTo(500.0)

    histogram.record(600.0)
    assertThat(metrics.histogramP50("call_times")).isEqualTo(450.0)
    assertThat(metrics.histogramP99("call_times")).isEqualTo(550.0)
  }

  @Test
  internal fun `get all samples`() {
    metrics.counter("counter", "-", listOf("foo")).labels("bar").inc()
    metrics.gauge("gauge", "-", listOf("foo")).labels("bar").inc()
    val quantiles = mapOf(0.5 to 0.5, 0.99 to 0.99)
    metrics.histogram("histogram", "-", listOf("foo"), quantiles).record(1.0, "bar")

    assertThat(metrics.getAllSamples().toList()).containsExactlyInAnyOrder(
      Sample("counter", listOf("foo"), listOf("bar"), 1.0),
      Sample("gauge", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram", listOf("foo", "quantile"), listOf("bar", "0.5"), 1.0),
      Sample("histogram", listOf("foo", "quantile"), listOf("bar", "0.99"), 1.0),
      Sample("histogram_count", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_sum", listOf("foo"), listOf("bar"), 1.0),
    )
  }
}
