package misk.metrics.v2

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
  internal fun `summary happy path`() {
    assertThat(metrics.get("call_times", "status" to "200")).isNull()
    val summary = metrics.summary("call_times", "-", labelNames = listOf("status"))
    summary.labels("200").observe(100.0)
    assertThat(metrics.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(metrics.summarySum("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(metrics.summaryCount("call_times", "status" to "200")).isEqualTo(1.0)
    assertThat(metrics.summaryP50("call_times", "status" to "200")).isEqualTo(100.0)
    summary.labels("200").observe(99.0)
    summary.labels("200").observe(101.0)
    assertThat(metrics.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(metrics.summarySum("call_times", "status" to "200")).isEqualTo(300.0)
    assertThat(metrics.summaryCount("call_times", "status" to "200")).isEqualTo(3.0)
    assertThat(metrics.summaryP50("call_times", "status" to "200")).isIn(99.0, 100.0, 101.0)
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
    assertThat(metrics.get("gets_total")).isNull()
    assertThat(metrics.get("puts_total")).isNull()

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
    assertThat(metrics.get("gets_total")).isEqualTo(17.0)
    assertThat(metrics.get("puts_total")).isEqualTo(29.0)
  }

  @Test
  internal fun `summary quantiles`() {
    val summary = metrics.summary("call_times", "-", labelNames = listOf())

    summary.observe(400.0)
    assertThat(metrics.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(metrics.summaryP99("call_times")).isEqualTo(400.0)

    summary.observe(450.0)
    assertThat(metrics.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(metrics.summaryP99("call_times")).isEqualTo(450.0)

    summary.observe(500.0)
    assertThat(metrics.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(metrics.summaryP99("call_times")).isEqualTo(500.0)

    summary.observe(550.0)
    assertThat(metrics.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(metrics.summaryP99("call_times")).isEqualTo(550.0)

    summary.observe(600.0)
    assertThat(metrics.summaryP50("call_times")).isEqualTo(500.0)
    assertThat(metrics.summaryP99("call_times")).isEqualTo(600.0)
  }

  @Test
  internal fun `get all samples`() {
    metrics.counter("counter_total", "-", listOf("foo")).labels("bar").inc()
    metrics.gauge("gauge", "-", listOf("foo")).labels("bar").inc()
    metrics.histogram("histogram", "-", listOf("foo"), listOf(1.0, 2.0)).labels("bar").observe(1.0)

    assertThat(metrics.getAllSamples().toList()).contains(
      Sample("counter_total", listOf("foo"), listOf("bar"), 1.0),
      Sample("gauge", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "1.0"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "2.0"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "+Inf"), 1.0),
      Sample("histogram_count", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_sum", listOf("foo"), listOf("bar"), 1.0),
    )
  }
}
