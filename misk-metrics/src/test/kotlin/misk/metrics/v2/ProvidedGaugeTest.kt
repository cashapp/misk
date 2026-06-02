package misk.metrics.v2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

internal class ProvidedGaugeTest {

  @TestFactory
  fun `register provider for labeled gauge retrieve values`(): Iterable<DynamicTest> {
    val gauge = ProvidedGauge.builder("testgauge", "for testing").labelNames("name", "type").create()
    return listOf(arrayOf("label_one", "type_one"), arrayOf("label_two", "type_two")).map { labels ->
      val child = gauge.labels(*labels)
      dynamicTest("for labels '${labels.joinToString()}'") {
        val gaugeProvider =
          object {
            var value: Long = 0
          }
        child.registerProvider(gaugeProvider) { value }

        assertThat(child.get()).isEqualTo(gaugeProvider.value.toDouble())

        gaugeProvider.value = 100
        assertThat(child.get()).isEqualTo(gaugeProvider.value.toDouble())

        gaugeProvider.value = 5
        assertThat(child.get()).isEqualTo(gaugeProvider.value.toDouble())
      }
    }
  }
}
