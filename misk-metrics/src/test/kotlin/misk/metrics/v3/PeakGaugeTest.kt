package misk.metrics.v3

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import misk.metrics.v2.PeakGauge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PeakGaugeTest {

  /**
   * Verifies that a peak gauge records the correct maximum value and resets to the initial value
   * after [PeakGauge.getAndClear]
   */
  @Test fun `records maximum and resets on getAndClear`() {
    val gauge = Metrics.Companion.PeakGauge()

    // 0 is our implicit initial value
    assertThat(gauge.getAndClear()).isEqualTo(0.0)

    // We can read a value after setting it
    gauge.record(37.0)
    assertThat(gauge.getAndClear()).isEqualTo(37.0)

    // Clearing the value should reset it to the initial value
    assertThat(gauge.getAndClear()).isEqualTo(0.0)

    // A read after multiple sets returns the peak value
    gauge.record(10.0)
    gauge.record(5.0)
    gauge.record(15.0)
    gauge.record(20.0)
    gauge.record(1.0)

    assertThat(gauge.getAndClear()).isEqualTo(20.0)
  }

  /**
   * Verifies that [PeakGauge.collect] returns correct information and from and resets each
   * [PeakGauge].
   */
  @Test fun `collection returns correct information and resets each child`() {
    val registry = CollectorRegistry(true)
    val metrics = Metrics(PrometheusMeterRegistry(PrometheusConfig.DEFAULT, registry, Clock.SYSTEM))

    val gauge1 = Metrics.Companion.PeakGauge()
    metrics.gauge(
      "some_name", gauge1::getAndClear, "some help text", listOf(
      Tag.of("some_label_name", "some_label_value"),
      Tag.of("another_label_name", "another_label_value")
    )
    )
    gauge1.record(36.0)
    gauge1.record(37.0)

    val gauge2 = Metrics.Companion.PeakGauge()
    metrics.gauge(
      "some_name", gauge2::getAndClear, "some help text", listOf(
      Tag.of("some_label_name", "different_label_value"),
      Tag.of("another_label_name", "another_different_label_value")
    )
    )
    gauge2.record(31.0)
    gauge2.record(30.0)

    // Perform a collection. This will reset the gauges.
    val collection = registry.metricFamilySamples().asSequence().toList()

    // Verify they have been reset.
    assertThat(gauge1.getAndClear()).isEqualTo(0.0)
    assertThat(gauge2.getAndClear()).isEqualTo(0.0)

    // We have one set of label names
    assertThat(collection).hasSize(1);

    // We have 2 gauges with distinct label values: gauge1 and gauge2
    val byValue = collection[0].samples.groupBy { it.value }
    assertThat(byValue).hasSize(2)

    // The first gauge should have peak of 37
    assertThat(byValue.get(37.0)).hasSize(1)
    assertThat(byValue.get(37.0)!!.get(0).labelValues).containsAll(
      listOf(
        "some_label_value",
        "another_label_value"
      )
    )

    // The second gauge should have a peak of 31
    assertThat(byValue.get(31.0)).hasSize(1)
    assertThat(byValue.get(31.0)!!.get(0).labelValues).containsAll(
      listOf(
        "different_label_value",
        "another_different_label_value"
      )
    )
  }
}
