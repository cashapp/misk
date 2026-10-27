package misk.hibernate.vitess

import com.google.common.util.concurrent.ServiceManager
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Transacter
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ShardListFetcherShutdownTest {
  @MiskExternalDependency private val dockerVitess = DockerVitess()

  @MiskTestModule val module = MoviesTestModule(type = DataSourceType.VITESS_MYSQL, allowScatters = true)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var serviceManager: ServiceManager

  @Test
  fun `stopping the session factory releases the shard list fetcher thread`() {
    val before = fetcherThreads()
    transacter.transaction { session -> session.shards() }
    val started = fetcherThreads() - before
    assertThat(started).isNotEmpty()

    serviceManager.stopAsync().awaitStopped(30, TimeUnit.SECONDS)

    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (fetcherThreads().intersect(started).isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(50)
    assertThat(fetcherThreads().intersect(started)).isEmpty()
  }

  private fun fetcherThreads(): Set<String> =
    Thread.getAllStackTraces()
      .keys
      .filter { it.isAlive && it.name.startsWith("shard-list-fetcher-") }
      .map { it.name }
      .toSet()
}
