package misk.metrics.backends.stackdriver

import com.codahale.metrics.Gauge
import com.google.api.client.util.DateTime
import com.google.api.services.monitoring.v3.model.TimeSeries
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.util.Modules
import misk.config.AppName
import misk.environment.InstanceMetadata
import misk.metrics.Metrics
import misk.metrics.MetricsModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.time.FakeClockModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest
internal class StackDriverReporterTest {
  companion object {
    val APP_NAME = "my_app"
    val INSTANCE_METADATA = InstanceMetadata("165.33.45.25", "us-west2")
    val METRIC_PREFIX =
        "custom.googleapis.com/dw/$APP_NAME/${INSTANCE_METADATA.zone}/${INSTANCE_METADATA.instanceName}"
    val START_TIME = DateTime("2017-11-23T16:46:32Z")
    val NOW = DateTime("2017-11-24T21:56:30Z")

    fun assertGauge(
        timeSeries: TimeSeries,
        expected: Double
    ) {
      assertThat(timeSeries.metricKind).isEqualTo("GAUGE")
      assertThat(timeSeries.points).hasSize(1)
      assertThat(timeSeries.points[0].value.doubleValue).isCloseTo(
          expected, Percentage.withPercentage(0.1)
      )
      assertThat(timeSeries.points[0].interval.startTime).isNull()
      assertThat(timeSeries.points[0].interval.endTime).isEqualTo(NOW.toStringRfc3339())
    }

    fun assertGauge(
        timeSeries: TimeSeries,
        expected: Long
    ) {
      assertThat(timeSeries.metricKind).isEqualTo("GAUGE")
      assertThat(timeSeries.points).hasSize(1)
      assertThat(timeSeries.points[0].value.int64Value).isEqualTo(expected)
      assertThat(timeSeries.points[0].interval.startTime).isNull()
      assertThat(timeSeries.points[0].interval.endTime).isEqualTo(NOW.toStringRfc3339())
    }

    fun assertCumulative(
        timeSeries: TimeSeries,
        expected: Long
    ) {
      assertThat(timeSeries.metricKind).isEqualTo("CUMULATIVE")
      assertThat(timeSeries.points).hasSize(1)
      assertThat(timeSeries.points[0].value.int64Value).isEqualTo(expected)
      assertThat(timeSeries.points[0].interval.startTime)
          .isEqualTo(START_TIME.toStringRfc3339())
      assertThat(timeSeries.points[0].interval.endTime).isEqualTo(NOW.toStringRfc3339())
    }
  }

  @MiskTestModule
  val module = Modules.combine(
      MetricsModule(),
      FakeClockModule(),
      TestModule()
  )

  @Inject internal lateinit var metrics: Metrics
  @Inject internal lateinit var clock: FakeClock
  @Inject internal lateinit var reporter: StackDriverReporter

  @Test
  fun timer() {
    val timer = metrics.timer("foo")
    timer.update(10, TimeUnit.SECONDS)
    timer.update(11, TimeUnit.SECONDS)
    timer.update(9, TimeUnit.SECONDS)
    timer.update(12, TimeUnit.SECONDS)

    val timeSeries = reporter.toTimeSeries("foo", timer, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()

    assertCumulative(byType["$METRIC_PREFIX/foo/count"]!!, 4)
    assertGauge(byType["$METRIC_PREFIX/foo/max"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/min"]!!, 9000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/mean"]!!, 10500.0)
    assertGauge(byType["$METRIC_PREFIX/foo/stdDev"]!!, 1118.0339)
    assertGauge(byType["$METRIC_PREFIX/foo/p50"]!!, 11000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p75"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p95"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p98"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p99"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p999"]!!, 12000.0)
  }

  @Test
  fun counter() {
    val counter = metrics.counter("foo")
    counter.inc(75)

    val timeSeries = reporter.toTimeSeries("foo", counter, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertCumulative(byType["$METRIC_PREFIX/foo/count"]!!, 75)
  }

  @Test
  fun histogram() {
    val hist = metrics.histogram("foo")
    hist.update(10000)
    hist.update(11000)
    hist.update(9000)
    hist.update(12000)

    val timeSeries = reporter.toTimeSeries("foo", hist, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()

    assertCumulative(byType["$METRIC_PREFIX/foo/count"]!!, 4)
    assertGauge(byType["$METRIC_PREFIX/foo/max"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/min"]!!, 9000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/mean"]!!, 10500.0)
    assertGauge(byType["$METRIC_PREFIX/foo/stdDev"]!!, 1118.0339)
    assertGauge(byType["$METRIC_PREFIX/foo/p50"]!!, 11000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p75"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p95"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p98"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p99"]!!, 12000.0)
    assertGauge(byType["$METRIC_PREFIX/foo/p999"]!!, 12000.0)
  }

  @Test
  fun longGauge() {
    val f: () -> Long = { 42 }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42)
  }

  @Test
  fun intGauge() {
    val f: () -> Int = { 42 }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42)
  }

  @Test
  fun doubleGauge() {
    val f: () -> Double = { 42.4 }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42.4)

  }

  @Test
  fun floatGauge() {
    val f: () -> Float = { 42.4f }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42.4)
  }

  @Test
  fun byteGauge() {
    val f: () -> Byte = { 42 }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42)
  }

  @Test
  fun bigDecimalGauge() {
    val f: () -> BigDecimal = { BigDecimal.valueOf(42.4) }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42.4)
  }

  @Test
  fun bigIntegerGauge() {
    val f: () -> BigInteger = { BigInteger.valueOf(42) }
    val gauge: Gauge<Any> = metrics.gauge("foo", f)

    val timeSeries = reporter.toTimeSeries("foo", gauge, START_TIME, NOW)
    val byType = timeSeries.map { it.metric.type to it }
        .toMap()
    assertGauge(byType["$METRIC_PREFIX/foo/value"]!!, 42.toDouble())
  }

  @Test
  fun toTimeSeries() {
    metrics.timer("fiddy")
        .update(10, TimeUnit.MILLISECONDS)
    metrics.timer("fiddy")
        .update(23, TimeUnit.MILLISECONDS)
    metrics.counter("fenty")
        .inc(245)
    metrics.counter("rent")
        .inc(744)
    metrics.counter("kent")
        .inc(95)
    metrics.gauge("im-basic", { 42 })
    metrics.histogram("histy")
        .update(65)
    metrics.histogram("histy")
        .update(66)
    metrics.histogram("histy")
        .update(67)
    metrics.histogram("crows")
        .update(99)
    metrics.histogram("crows")
        .update(102)

    val timeSeries = reporter.toTimeSeries(
        metrics.gauges,
        metrics.counters,
        metrics.histograms,
        null,
        metrics.timers,
        START_TIME, NOW
    )

    val names = timeSeries.map { it.metric.type to it }
        .toMap()
    assertThat(names.keys).containsExactly(
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/im-basic/value",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fenty/count",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/kent/count",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/rent/count",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/count",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/max",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/min",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/mean",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/stdDev",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/p50",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/p75",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/p95",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/p98",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/p99",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/crows/p999",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/count",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/max",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/min",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/mean",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/stdDev",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/p50",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/p75",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/p95",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/p98",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/p99",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/histy/p999",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/max",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/min",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/mean",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/stdDev",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/p50",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/p75",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/p95",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/p98",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/p99",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/p999",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/count",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/m1_rate",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/mean_rate",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/m5_rate",
        "custom.googleapis.com/dw/my_app/us-west2/165.33.45.25/fiddy/m15_rate"
    )
  }

  class TestModule : AbstractModule() {
    override fun configure() {
    }

    @Provides
    @Singleton
    fun stackDriverSender(): StackDriverSender = object : StackDriverSender {
      override fun send(timeSeries: List<TimeSeries>) {}
    }

    @Provides
    @Singleton
    fun instanceMetadata(): InstanceMetadata = INSTANCE_METADATA

    @Provides
    @AppName
    fun appName(): String = APP_NAME
  }
}
