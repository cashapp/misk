package misk.hibernate

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.healthchecks.HealthCheck
import misk.jdbc.SchemaMigratorService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.getLogger
import wisp.time.FakeClock
import java.sql.Timestamp

@MiskTest(startService = true)
class DisabledHealthCheckTest {
  @MiskTestModule
  val module = MoviesTestModule(installHealthChecks = false)

  @Inject @Movies private lateinit var sessionFactoryService: Provider<SessionFactoryService>
  @Inject private lateinit var fakeClock: FakeClock
  @Inject private lateinit var healthChecks: List<HealthCheck>

  @Test
  fun isNotInjected() {
    assertThat(healthChecks).noneMatch { it is HibernateHealthCheck }
    assertThat(healthChecks).noneMatch { it is SchemaMigratorService }
  }

  @Test
  fun isStillConnectedToDb() {
    val databaseInstant = try {
      val sessionFactory = sessionFactoryService.get().sessionFactory
      sessionFactory.openSession().use { session ->
        session.createNativeQuery("SELECT NOW()").uniqueResult() as Timestamp
      }.toInstant()
    } catch (e: Exception) {
      logger.error(e) { "error performing hibernate health check" }
      null
    }

    assertThat(databaseInstant).isNotNull()
    assertThat(databaseInstant).isAfterOrEqualTo(fakeClock.instant())
  }

  companion object {
    val logger = getLogger<DisabledHealthCheckTest>()
  }
}
