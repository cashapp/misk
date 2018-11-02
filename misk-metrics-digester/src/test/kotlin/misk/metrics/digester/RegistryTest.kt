package misk.metrics.digester

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class RegistryTest {

  @Test
  fun registryDigest() {
    val registry = TDigestHistogramRegistry<FakeDigest>(
        fun() = SlidingWindowDigest(
            Windower(windowSecs = 30, stagger = 3), // this does not matter
            fun() = FakeDigest()))

    val histogram = registry.newHistogram("name", "help", listOf(), mapOf(0.1 to 0.1, 0.2 to 0.2))
    histogram.record(1.23, "test")

    val firstWindow = histogram.getMetric("test")!!.digest.windows[0].digest as FakeDigest

    // Check that recorded value gets added to the metric
    Assertions.assertThat(firstWindow.addedValues).isEqualTo(arrayListOf(1.23))

    histogram.record(4.56, "test")
    Assertions.assertThat(firstWindow.addedValues).isEqualTo(arrayListOf(1.23, 4.56))

    // Add a different metric name
    histogram.record(2.34, "another_test")

    // Check that a new metric gets created for the new key
    Assertions.assertThat(histogram.metrics.keys.count()).isEqualTo(2)
  }
}