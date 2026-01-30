package misk.micrometer.prometheus

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.metrics.MetricsModule
import misk.metrics.v2.Metrics
import misk.micrometer.MicrometerModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class MicrometerPrometheusModuleTest {
  @MiskTestModule
  val module =
    object : misk.inject.KAbstractModule() {
      override fun configure() {
        install(MetricsModule())
        install(MicrometerModule())
        install(MicrometerPrometheusModule())
      }
    }

  @Inject lateinit var meterRegistry: MeterRegistry
  @Inject lateinit var prometheusRegistry: CollectorRegistry
  @Inject lateinit var prometheusMeterRegistry: PrometheusMeterRegistry
  @Inject lateinit var legacyMetrics: Metrics

  @Test
  fun `provides prometheus meter registry`() {
    assertThat(prometheusMeterRegistry).isNotNull
  }

  @Test
  fun `shares prometheus registry with legacy metrics`() {
    // This verifies that both systems use the same underlying Prometheus registry
    val sharedRegistry = prometheusMeterRegistry.prometheusRegistry
    assertThat(sharedRegistry).isSameAs(prometheusRegistry)
  }

  @Test
  fun `micrometer metrics appear in prometheus scrape`() {
    // Create a Micrometer counter
    val counter =
      Counter.builder("micrometer.test.counter")
        .description("Test counter via Micrometer")
        .tag("source", "micrometer")
        .register(meterRegistry)

    counter.increment(42.0)

    // Scrape the Prometheus registry
    val scrape = prometheusMeterRegistry.scrape()

    assertThat(scrape).contains("micrometer_test_counter")
    assertThat(scrape).contains("source=\"micrometer\"")
    assertThat(scrape).contains("42.0")
  }

  @Test
  fun `legacy metrics appear in prometheus scrape`() {
    // Create a legacy counter
    val legacyCounter =
      legacyMetrics.counter("legacy_test_counter", "Test counter via legacy metrics", listOf("source"))

    legacyCounter.labels("legacy").inc(15.0)

    // Scrape the Prometheus registry
    val scrape = prometheusMeterRegistry.scrape()

    assertThat(scrape).contains("legacy_test_counter")
    assertThat(scrape).contains("source=\"legacy\"")
    assertThat(scrape).contains("15.0")
  }

  @Test
  fun `both micrometer and legacy metrics coexist`() {
    // Create metrics from both systems
    val micrometerCounter = Counter.builder("app.requests").tag("type", "micrometer").register(meterRegistry)

    val legacyCounter = legacyMetrics.counter("app_operations", "Legacy operations", listOf("type"))

    micrometerCounter.increment(10.0)
    legacyCounter.labels("legacy").inc(20.0)

    val scrape = prometheusMeterRegistry.scrape()

    // Both should appear in the same scrape output
    assertThat(scrape).contains("app_requests")
    assertThat(scrape).contains("app_operations")
  }

  @Test
  fun `prometheus meter registry is added to composite`() {
    // The Prometheus registry should be one of the registries in the composite
    val composite = meterRegistry as io.micrometer.core.instrument.composite.CompositeMeterRegistry
    val registries = composite.registries

    assertThat(registries).contains(prometheusMeterRegistry)
  }
}
