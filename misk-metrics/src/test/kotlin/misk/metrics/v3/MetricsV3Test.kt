package misk.metrics.v3

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import misk.metrics.MetricsV3Module
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class MetricsV3Test {
  @MiskTestModule
  val module =
    object : misk.inject.KAbstractModule() {
      override fun configure() {
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
        install(MetricsV3Module())
      }
    }

  @Inject lateinit var metrics: Metrics
  @Inject lateinit var meterRegistry: MeterRegistry

  @Test
  fun `counter increments correctly`() {
    val counter = metrics.counter("test.counter", "Test counter", listOf("env"))

    counter.labels("dev").inc()
    counter.labels("dev").inc(5.0)
    counter.labels("prod").inc(10.0)

    val devCounter = meterRegistry.find("test.counter").tag("env", "dev").counter()
    assertThat(devCounter).isNotNull
    assertThat(devCounter!!.count()).isEqualTo(6.0)

    val prodCounter = meterRegistry.find("test.counter").tag("env", "prod").counter()
    assertThat(prodCounter).isNotNull
    assertThat(prodCounter!!.count()).isEqualTo(10.0)
  }

  @Test
  fun `gauge set and get work correctly`() {
    val gauge = metrics.gauge("test.gauge", "Test gauge", listOf("service"))

    gauge.labels("api").set(42.0)
    assertThat(gauge.labels("api").get()).isEqualTo(42.0)

    gauge.labels("api").inc(8.0)
    assertThat(gauge.labels("api").get()).isEqualTo(50.0)

    gauge.labels("api").dec(10.0)
    assertThat(gauge.labels("api").get()).isEqualTo(40.0)

    val meterGauge = meterRegistry.find("test.gauge").tag("service", "api").gauge()
    assertThat(meterGauge).isNotNull
    assertThat(meterGauge!!.value()).isEqualTo(40.0)
  }

  @Test
  fun `peakGauge resets after read`() {
    val peakGauge = metrics.peakGauge("test.peak_gauge", "Test peak gauge", listOf("type"))

    peakGauge.labels("request").set(100.0)
    assertThat(peakGauge.labels("request").get()).isEqualTo(100.0)

    val meterGauge = meterRegistry.find("test.peak_gauge").tag("type", "request").gauge()
    assertThat(meterGauge).isNotNull
    assertThat(meterGauge!!.value()).isEqualTo(100.0)

    assertThat(meterGauge.value()).isEqualTo(0.0)
  }

  @Test
  fun `providedGauge uses supplier`() {
    val providedGauge = metrics.providedGauge("test.provided_gauge", "Test provided gauge", listOf("source"))

    var value = 10.0
    providedGauge.labels("external").setSupplier { value }

    val meterGauge = meterRegistry.find("test.provided_gauge").tag("source", "external").gauge()
    assertThat(meterGauge).isNotNull
    assertThat(meterGauge!!.value()).isEqualTo(10.0)

    value = 25.0
    assertThat(meterGauge.value()).isEqualTo(25.0)
  }

  @Test
  fun `histogram records observations`() {
    val histogram = metrics.histogram("test.histogram", "Test histogram", listOf("endpoint"))

    histogram.labels("/api").observe(100.0)
    histogram.labels("/api").observe(200.0)
    histogram.labels("/api").observe(300.0)

    val summary = meterRegistry.find("test.histogram").tag("endpoint", "/api").summary()
    assertThat(summary).isNotNull
    assertThat(summary!!.count()).isEqualTo(3)
    assertThat(summary.totalAmount()).isEqualTo(600.0)
  }

  @Test
  fun `histogram timeMs records duration`() {
    val histogram = metrics.histogram("test.duration", "Test duration", listOf("operation"))

    val elapsed = histogram.labels("compute").timeMs { Thread.sleep(10) }

    assertThat(elapsed).isGreaterThanOrEqualTo(10.0)

    val summary = meterRegistry.find("test.duration").tag("operation", "compute").summary()
    assertThat(summary).isNotNull
    assertThat(summary!!.count()).isEqualTo(1)
    assertThat(summary.totalAmount()).isGreaterThanOrEqualTo(10.0)
  }

  @Test
  fun `summary records observations`() {
    val summary = metrics.summary("test.summary", "Test summary", listOf("method"))

    summary.labels("GET").observe(50.0)
    summary.labels("GET").observe(150.0)
    summary.labels("POST").observe(300.0)

    val getSummary = meterRegistry.find("test.summary").tag("method", "GET").summary()
    assertThat(getSummary).isNotNull
    assertThat(getSummary!!.count()).isEqualTo(2)
    assertThat(getSummary.totalAmount()).isEqualTo(200.0)

    val postSummary = meterRegistry.find("test.summary").tag("method", "POST").summary()
    assertThat(postSummary).isNotNull
    assertThat(postSummary!!.count()).isEqualTo(1)
    assertThat(postSummary.totalAmount()).isEqualTo(300.0)
  }

  @Test
  fun `counter without labels works`() {
    val counter = metrics.counter("simple.counter", "Simple counter")

    counter.labels().inc()
    counter.labels().inc(2.0)

    val meterCounter = meterRegistry.find("simple.counter").counter()
    assertThat(meterCounter).isNotNull
    assertThat(meterCounter!!.count()).isEqualTo(3.0)
  }
}
