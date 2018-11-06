package misk.metrics.digester

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistryTest {

  @Test
  fun registyDigest() {
    val registry = histogramRegistryFactory()

    val histogram = registry.newHistogram("name", "help", listOf(), mapOf(0.1 to 0.1, 0.2 to 0.2))
    val metric = histogram.labels()
    metric.observe(1.23)
    metric.observe(4.56)

    val firstWindow = histogram.getMetric().digest.windows[0].digest as FakeDigest
    assertThat(firstWindow.addedValues).isEqualTo(listOf(1.23, 4.56))
    assertThat(histogram.count()).isEqualTo(3)
  }

  @Test
  fun registryDigestWithLabels() {
    val registry = histogramRegistryFactory()

    val histogram = registry.newHistogram("name", "help", listOf(), mapOf(0.1 to 0.1, 0.2 to 0.2))
    val metric = histogram.labels("test")
    metric.observe(1.23)

    val firstWindow = histogram.getMetric("test").digest.windows[0].digest as FakeDigest

    // Check that recorded value gets added to the metric
    assertThat(firstWindow.addedValues).isEqualTo(listOf(1.23))
    metric.observe(4.56)

    // Check that both values are added to the window
    assertThat(firstWindow.addedValues).isEqualTo(listOf(1.23, 4.56))
    assertThat(histogram.count("test")).isEqualTo(3)

    // Add a different metric name
    histogram.labels("another_test", "another_test_2").observe(2.34)

    // Check that a new metric gets created for the new key
    assertThat(histogram.getMetric("another_test", "another_test_2")).isNotNull()
    assertThat(histogram.getMetric("another_test_2", "another_test")).isNotNull()
  }

  fun histogramRegistryFactory(): TDigestHistogramRegistry<FakeDigest> {
    return TDigestHistogramRegistry(
        fun() = SlidingWindowDigest(
            Windower(windowSecs = 30, stagger = 3), // this does not matter
            fun() = FakeDigest()))
  }
}