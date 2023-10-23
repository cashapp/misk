package misk.metrics.v3

import com.google.common.util.concurrent.AtomicDouble
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples.Sample
import io.prometheus.client.CollectorRegistry
import misk.metrics.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import misk.metrics.v2.Metrics as V2Metrics

class MetricsTest {
  private lateinit var promRegistry: CollectorRegistry
  private lateinit var micrometerRegistry: CollectorRegistry
  private lateinit var metrics: Metrics
  private lateinit var v2Metrics: V2Metrics

  @BeforeEach
  internal fun beforeEach() {
    micrometerRegistry = CollectorRegistry(true)
    metrics =
      Metrics(PrometheusMeterRegistry(PrometheusConfig.DEFAULT, micrometerRegistry, Clock.SYSTEM))

    promRegistry = CollectorRegistry(true)
    v2Metrics = V2Metrics.factory(promRegistry, metrics)
  }

  @Test
  internal fun `count happy path`() {
    // do everything with Prometheus
    assertThat(promRegistry.get("gets", "status" to "200")).isNull()
    val promCounter = v2Metrics.counter("gets", "-", labelNames = listOf("status"))
      .labels("200")
    promCounter.inc()
    assertThat(promRegistry.get("gets", "status" to "200")).isEqualTo(1.0)
    promCounter.inc()
    assertThat(promRegistry.get("gets", "status" to "200")).isEqualTo(2.0)

    // do the same things with micrometer
    assertThat(micrometerRegistry.get("gets", "status" to "200")).isNull()
    val counter = metrics.counter("gets", "-", listOf(Tag.of("status", "200")))
    counter.increment()
    assertThat(micrometerRegistry.get("gets", "status" to "200")).isEqualTo(1.0)
    counter.increment()
    assertThat(micrometerRegistry.get("gets", "status" to "200")).isEqualTo(2.0)

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  private fun assertThatPromAndMicrometerRegistriesAreEquivalent(
    promRegistry: CollectorRegistry,
    micrometerRegistry: CollectorRegistry
  ) {
    val micrometerMetrics =
      micrometerRegistry.metricFamilySamples().asSequence()
        .associateBy { it.name + it.type.toString() }
        .toMutableMap()
    promRegistry.metricFamilySamples().asSequence().forEach { promMetric ->
      val micrometerMetric = micrometerMetrics.remove(promMetric.name + promMetric.type.toString())
        ?: fail("expected metric with name ${promMetric.name} and type ${promMetric.type} to be registered in micrometer but was missing (it was in prometheus)")
      // micrometer will add a gauge with a max to distribution summaries and we need to filter it out here
      if (micrometerMetric.type in METRIC_TYPES_WITH_ADDITIONAL_GAUGES) {
        micrometerMetrics.remove(promMetric.name + "_max" + Collector.Type.GAUGE.toString())
      }
      assertThat(promMetric.help).isEqualTo(micrometerMetric.help)
      assertSamplesAreEquivalent(promMetric.samples, micrometerMetric.samples)
    }

    assertThat(micrometerMetrics).withFailMessage("There should be no unprocesed prometheus metrics")
      .isEmpty()
  }

  private fun assertSamplesAreEquivalent(
    promSamples: List<Sample>,
    micometerSamples: List<Sample>
  ) {
    val promMap = promSamples.filter { !it.name.endsWith("_created") }
      .associateBy { it.name + it.labelNames.toString() + it.labelValues.toString() }.toMutableMap()
    micometerSamples.forEach { micrommeterSample ->
      val promSample =
        promMap.remove(micrommeterSample.name + micrommeterSample.labelNames.toString() + micrommeterSample.labelValues.toString())
          ?: fail("expected to find a sample with matching name and tags [${micrommeterSample.name + micrommeterSample.labelNames.toString() + micrommeterSample.labelValues.toString()}] in the promethues samples for this metric")
      assertThat(micrommeterSample.exemplar).isEqualTo(promSample.exemplar)
      assertThat(micrommeterSample.value).isCloseTo(
        promSample.value,
        Percentage.withPercentage(0.1)
      )
      if (micrommeterSample.timestampMs != null && promSample.timestampMs != null) {
        assertThat(micrommeterSample.timestampMs).isEqualTo(promSample.timestampMs)
      }
    }
    assertThat(promMap).withFailMessage("There should be no unprocesed prometheus samples")
      .isEmpty()
  }

  @Test
  internal fun `gauge happy path`() {
    // do everything with Prometheus
    assertThat(promRegistry.get("thread_count", "state" to "running")).isNull()
    val promGauge = v2Metrics.gauge("thread_count", "-", labelNames = listOf("state"))
      .labels("running")
    promGauge.set(20.0)
    assertThat(promRegistry.get("thread_count", "state" to "running")).isEqualTo(20.0)
    promGauge.set(30.0)
    assertThat(promRegistry.get("thread_count", "state" to "running")).isEqualTo(30.0)

    // do the same things with micrometer
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isNull()
    val gaugeValue = AtomicDouble()
    metrics.gauge("thread_count", gaugeValue::get, "-", listOf(Tag.of("state", "running")))
    gaugeValue.set(20.0)
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isEqualTo(20.0)
    gaugeValue.set(30.0)
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isEqualTo(30.0)

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  @Test
  internal fun `peakGauge happy path`() {
    // do everything with Prometheus
    assertThat(promRegistry.get("thread_count", "state" to "running")).isNull()
    val promPeakGauge = v2Metrics.peakGauge("thread_count", "-", labelNames = listOf("state"))
      .labels("running")
    promPeakGauge.record(10.0)
    promPeakGauge.record(20.0)
    assertThat(promRegistry.get("thread_count", "state" to "running")).isEqualTo(20.0)
    // Another get without a set should result in seeing the initial value (0)
    assertThat(promRegistry.get("thread_count", "state" to "running")).isEqualTo(0.0)
    promPeakGauge.record(30.0)
    promPeakGauge.record(20.0)
    assertThat(promRegistry.get("thread_count", "state" to "running")).isEqualTo(30.0)
    // Another get without a set should result in seeing the initial value (0)
    assertThat(promRegistry.get("thread_count", "state" to "running")).isEqualTo(0.0)

    // do the same things with micrometer
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isNull()
    val peakGauge = metrics.peakGauge("thread_count", "-", Tags.of("state", "running"))
    peakGauge.record(10.0)
    peakGauge.record(20.0)
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isEqualTo(20.0)
    // Another get without a set should result in seeing the initial value (0)
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isEqualTo(0.0)
    peakGauge.record(30.0)
    peakGauge.record(20.0)
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isEqualTo(30.0)
    // Another get without a set should result in seeing the initial value (0)
    assertThat(micrometerRegistry.get("thread_count", "state" to "running")).isEqualTo(0.0)

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  @Test
  internal fun `summary happy path`() {
    assertThat(promRegistry.get("call_times", "status" to "200")).isNull()
    val promSummary = v2Metrics.summary("call_times", "-", labelNames = listOf("status"))
    promSummary.labels("200").observe(100.0)
    assertThat(promRegistry.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(promRegistry.summarySum("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(promRegistry.summaryCount("call_times", "status" to "200")).isEqualTo(1.0)
    assertThat(promRegistry.summaryP50("call_times", "status" to "200")).isEqualTo(100.0)
    promSummary.labels("200").observe(99.0)
    promSummary.labels("200").observe(101.0)
    assertThat(promRegistry.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(promRegistry.summarySum("call_times", "status" to "200")).isEqualTo(300.0)
    assertThat(promRegistry.summaryCount("call_times", "status" to "200")).isEqualTo(3.0)
    assertThat(promRegistry.summaryP50("call_times", "status" to "200")).isIn(99.0, 100.0, 101.0)

    // do the same things with micrometer
    assertThat(micrometerRegistry.get("call_times", "status" to "200")).isNull()
    val summary = metrics.summary("call_times", "-", listOf(Tag.of("status", "200")))
    summary.record(100.0)
    assertThat(micrometerRegistry.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(micrometerRegistry.summarySum("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(micrometerRegistry.summaryCount("call_times", "status" to "200")).isEqualTo(1.0)
    assertThat(micrometerRegistry.summaryP50("call_times", "status" to "200")).isEqualTo(100.0)
    summary.record(99.0)
    summary.record(101.0)
    assertThat(micrometerRegistry.summaryMean("call_times", "status" to "200")).isEqualTo(100.0)
    assertThat(micrometerRegistry.summarySum("call_times", "status" to "200")).isEqualTo(300.0)
    assertThat(micrometerRegistry.summaryCount("call_times", "status" to "200")).isEqualTo(3.0)
    assertThat(micrometerRegistry.summaryP50("call_times", "status" to "200")).isIn(
      99.0,
      100.0,
      101.0
    )

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  @Test
  internal fun `different label values`() {
    assertThat(promRegistry.get("gets", "status" to "200")).isNull()
    assertThat(promRegistry.get("gets", "status" to "503")).isNull()

    val counter = v2Metrics.counter("gets", "-", labelNames = listOf("status"))
    val promCounter200s = counter.labels("200")
    val promCounter503s = counter.labels("503")

    promCounter200s.inc(7.0)
    promCounter503s.inc(9.0)
    assertThat(promRegistry.get("gets", "status" to "200")).isEqualTo(7.0)
    assertThat(promRegistry.get("gets", "status" to "503")).isEqualTo(9.0)

    promCounter200s.inc(10.0)
    promCounter503s.inc(20.0)
    assertThat(promRegistry.get("gets", "status" to "200")).isEqualTo(17.0)
    assertThat(promRegistry.get("gets", "status" to "503")).isEqualTo(29.0)

    // do the same things with micrometer
    assertThat(micrometerRegistry.get("gets", "status" to "200")).isNull()
    assertThat(micrometerRegistry.get("gets", "status" to "503")).isNull()

    val counter200s = metrics.counter("gets", "-", listOf(Tag.of("status", "200")))
    val counter503s = metrics.counter("gets", "-", listOf(Tag.of("status", "503")))

    counter200s.increment(7.0)
    counter503s.increment(9.0)
    assertThat(micrometerRegistry.get("gets", "status" to "200")).isEqualTo(7.0)
    assertThat(micrometerRegistry.get("gets", "status" to "503")).isEqualTo(9.0)

    counter200s.increment(10.0)
    counter503s.increment(20.0)
    assertThat(micrometerRegistry.get("gets", "status" to "200")).isEqualTo(17.0)
    assertThat(micrometerRegistry.get("gets", "status" to "503")).isEqualTo(29.0)

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  @Test
  internal fun `different names`() {
    assertThat(promRegistry.get("gets")).isNull()
    assertThat(promRegistry.get("puts")).isNull()
    assertThat(promRegistry.get("gets_total")).isNull()
    assertThat(promRegistry.get("puts_total")).isNull()

    val promGetsCounter = v2Metrics.counter("gets", "-")
    val promPutsCounter = v2Metrics.counter("puts", "-")

    promGetsCounter.inc(7.0)
    promPutsCounter.inc(9.0)
    assertThat(promRegistry.get("gets")).isEqualTo(7.0)
    assertThat(promRegistry.get("puts")).isEqualTo(9.0)

    promGetsCounter.inc(10.0)
    promPutsCounter.inc(20.0)
    assertThat(promRegistry.get("gets")).isEqualTo(17.0)
    assertThat(promRegistry.get("puts")).isEqualTo(29.0)
    assertThat(promRegistry.get("gets_total")).isEqualTo(17.0)
    assertThat(promRegistry.get("puts_total")).isEqualTo(29.0)

    // do the same things with micrometer
    assertThat(micrometerRegistry.get("gets")).isNull()
    assertThat(micrometerRegistry.get("puts")).isNull()
    assertThat(micrometerRegistry.get("gets_total")).isNull()
    assertThat(micrometerRegistry.get("puts_total")).isNull()

    val getsCounter = metrics.counter("gets", "-")
    val putsCounter = metrics.counter("puts", "-")

    getsCounter.increment(7.0)
    putsCounter.increment(9.0)
    assertThat(micrometerRegistry.get("gets")).isEqualTo(7.0)
    assertThat(micrometerRegistry.get("puts")).isEqualTo(9.0)

    getsCounter.increment(10.0)
    putsCounter.increment(20.0)
    assertThat(micrometerRegistry.get("gets")).isEqualTo(17.0)
    assertThat(micrometerRegistry.get("puts")).isEqualTo(29.0)
    assertThat(micrometerRegistry.get("gets_total")).isEqualTo(17.0)
    assertThat(micrometerRegistry.get("puts_total")).isEqualTo(29.0)

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  @Test
  internal fun `summary quantiles`() {
    val promSummary = v2Metrics.summary("call_times", "-", labelNames = listOf())

    promSummary.observe(400.0)
    assertThat(promRegistry.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(promRegistry.summaryP99("call_times")).isEqualTo(400.0)

    promSummary.observe(450.0)
    assertThat(promRegistry.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(promRegistry.summaryP99("call_times")).isEqualTo(450.0)

    promSummary.observe(500.0)
    assertThat(promRegistry.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(promRegistry.summaryP99("call_times")).isEqualTo(500.0)

    promSummary.observe(550.0)
    assertThat(promRegistry.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(promRegistry.summaryP99("call_times")).isEqualTo(550.0)

    promSummary.observe(600.0)
    assertThat(promRegistry.summaryP50("call_times")).isEqualTo(500.0)
    assertThat(promRegistry.summaryP99("call_times")).isEqualTo(600.0)

    // do the same things with micrometer
    val summary = metrics.summary("call_times", "-")

    summary.record(400.0)
    assertThat(micrometerRegistry.summaryP50("call_times")).isEqualTo(400.0)
    assertThat(micrometerRegistry.summaryP99("call_times")).isEqualTo(400.0)

    summary.record(450.0)
    assertThat(micrometerRegistry.summaryP50("call_times")).isEqualTo(400.0)
    //FIXME: Values should probably match prometheus, so something weird is happening.
    assertThat(micrometerRegistry.summaryP99("call_times")).isEqualTo(450.0)

    summary.record(500.0)
    assertThat(micrometerRegistry.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(micrometerRegistry.summaryP99("call_times")).isEqualTo(500.0)

    summary.record(550.0)
    assertThat(micrometerRegistry.summaryP50("call_times")).isEqualTo(450.0)
    assertThat(micrometerRegistry.summaryP99("call_times")).isEqualTo(550.015625)//550

    summary.record(600.0)
    assertThat(micrometerRegistry.summaryP50("call_times")).isEqualTo(500.0)
    assertThat(micrometerRegistry.summaryP99("call_times")).isEqualTo(600.015625)//600

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  @Test
  internal fun `get all samples`() {
    v2Metrics.counter("counter_total", "-", listOf("foo")).labels("bar").inc()
    v2Metrics.gauge("gauge", "-", listOf("foo")).labels("bar").inc()
    v2Metrics.histogram("histogram", "-", listOf("foo"), listOf(1.0, 2.0)).labels("bar")
      .observe(1.0)

    assertThat(promRegistry.getAllSamples().toList()).contains(
      Sample("counter_total", listOf("foo"), listOf("bar"), 1.0),
      Sample("gauge", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "1.0"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "2.0"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "+Inf"), 1.0),
      Sample("histogram_count", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_sum", listOf("foo"), listOf("bar"), 1.0),
    )

    // do the same things with micrometer
    metrics.counter("counter_total", "-", listOf(Tag.of("foo", "bar"))).increment()
    val gauge = AtomicDouble()
    metrics.gauge("gauge", gauge::get, "-", listOf(Tag.of("foo", "bar")))
    gauge.set(1.0)
    metrics.histogram("histogram", "-", listOf(Tag.of("foo", "bar")), listOf(1.0, 2.0))
      .record(1.0)

    assertThat(micrometerRegistry.getAllSamples().toList()).contains(
      Sample("counter_total", listOf("foo"), listOf("bar"), 1.0),
      Sample("gauge", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "1.0"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "2.0"), 1.0),
      Sample("histogram_bucket", listOf("foo", "le"), listOf("bar", "+Inf"), 1.0),
      Sample("histogram_count", listOf("foo"), listOf("bar"), 1.0),
      Sample("histogram_sum", listOf("foo"), listOf("bar"), 1.0),
    )

    assertThatPromAndMicrometerRegistriesAreEquivalent(promRegistry, micrometerRegistry)
  }

  companion object {
    private val METRIC_TYPES_WITH_ADDITIONAL_GAUGES =
      setOf(Collector.Type.HISTOGRAM, Collector.Type.GAUGE_HISTOGRAM, Collector.Type.SUMMARY)
  }
}
