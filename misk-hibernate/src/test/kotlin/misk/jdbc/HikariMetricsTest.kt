package misk.jdbc

import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.hibernate.MoviesTestModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

@MiskTest(startService = true)
class HikariMetricsTest {
  @MiskExternalDependency
  private val dockerVitess = DockerVitess

  @MiskTestModule
  val module = MoviesTestModule()

  @Inject lateinit var registry: CollectorRegistry

  @Test
  fun metricsExist() {
    val metrics = Collections.list(registry.metricFamilySamples())
    assertThat(metrics).filteredOn { m -> m.name.startsWith("hikaricp") }.isNotEmpty
  }
}
