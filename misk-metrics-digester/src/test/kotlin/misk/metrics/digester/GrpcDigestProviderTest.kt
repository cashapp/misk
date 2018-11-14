package misk.metrics.digester

import misk.time.FakeClock
import org.junit.jupiter.api.Test

class GrpcDigestProviderTest {

  @Test
  fun DigestProviderService() {
    val clock = FakeClock()
    val windower = Windower(10, 1)
    val registry = TDigestHistogramRegistry(fun() = SlidingWindowDigest<VeneurDigest>(windower, fun() = VeneurDigest()))

    val vec1 = registry.newHistogram("name_1", "help_1", listOf(), mapOf(0.1 to 0.1, 0.2 to 0.2))

    val vec1Metric1 = vec1.getMetric("label_1").labels()
  }
}