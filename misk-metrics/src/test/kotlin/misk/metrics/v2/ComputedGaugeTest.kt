package misk.metrics.v2

import ComputedGauge
import io.prometheus.client.Collector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ComputedGaugeTest {

  private lateinit var valueHolder : ValueHolder

  class ValueHolder : ComputedGauge.ValueSupplier {
    private var value: Double = 0.0

    override fun get(labelNames: List<String>, labelValues: List<String>): Double {
      val labels = labelNames.zip(labelValues).toMap()
      labels.forEach { labelName, labelValue ->
        when (labelName) {
          "MULTIPLY" -> value *= labelValue.toDouble()
          "ADD" -> value += labelValue.toDouble()
        }
      }

      return value
    }

    internal fun set(value : Double) {
      this.value = value
    }
  }

  @BeforeEach fun setup() {
    valueHolder = ValueHolder()
  }


  @Test fun `no labels`() {
    valueHolder.set(37.0)
    val gauge = ComputedGauge.builder("some_name", "some help text", valueHolder).create()

    val samplesList: MutableList<Collector.MetricFamilySamples> = gauge.collect();
    assertThat(samplesList).hasSize(1)
    assertThat(samplesList.first().name).isEqualTo("some_name")
    assertThat(samplesList.first().samples).hasSize(1)
    assertThat(samplesList.first().samples.first().value).isEqualTo(37.0)
  }
}
