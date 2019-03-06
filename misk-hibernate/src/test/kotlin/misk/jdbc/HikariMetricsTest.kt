package misk.jdbc

import misk.hibernate.MoviesTestModule
import misk.metrics.Metrics
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections
import javax.inject.Inject

@MiskTest(startService = true)
class HikariMetricsTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject lateinit var metrics: Metrics

  @Test
  fun metricsExist() {
    val metrics = Collections.list(metrics.registry.metricFamilySamples())
    assertThat(metrics).filteredOn { m -> m.name.startsWith("hikaricp") }.isNotEmpty()
  }
}
