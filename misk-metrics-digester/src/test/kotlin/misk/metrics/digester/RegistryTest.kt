package misk.metrics.digester

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistryTest {

  @Test
  fun registryDigest() {
    val registry = TDigestHistogramRegistry(
        fun() = SlidingWindowDigest(
            Windower(windowSecs = 30, stagger = 3), // this does not matter
            fun() = FakeDigest()))

    val histogram = registry.newHistogram("name", "help", listOf(), mapOf(0.1 to 0.1, 0.2 to 0.2))
    histogram.labels("test").observe(1.23)

    val firstWindow = histogram.getMetric("test").digest.windows[0].digest as FakeDigest

    // Check that recorded value gets added to the metric
    assertThat(firstWindow.addedValues).isEqualTo(listOf(1.23))

    histogram.labels("test").observe(4.56)
    assertThat(firstWindow.addedValues).isEqualTo(listOf(1.23, 4.56))

    // Add a different metric name
    histogram.labels( "another_test", "another_test_2").observe(2.34)

    // Check that a new metric gets created for the new key
    assertThat(histogram.getMetric("another_test", "another_test_2")).isNotNull()
    assertThat(histogram.getMetric("another_test_2", "another_test")).isNotNull()
  }
}