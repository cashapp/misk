package misk.hibernate

import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.jdbc.DataSourceConfig
import misk.logging.getLogger
import org.hibernate.SessionFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration

private val logger = getLogger<HibernateHealthCheck>()

/**
 * HealthCheck to confirm database connectivity and defend against clock skew.
 */
class HibernateHealthCheck(
  private val sessionFactory: SessionFactory,
  private val config: DataSourceConfig,
  private val clock: Clock
) : HealthCheck {

  override fun status(): HealthStatus {
    val databaseInstant = try {
      sessionFactory.openSession().use { session ->
        session.createNativeQuery("SELECT NOW()").uniqueResult() as Timestamp
      }.toInstant()
    } catch (e: Exception) {
      return HealthStatus.unhealthy("Hibernate: failed to query ${config.database} database")
    }

    val delta = Duration.between(clock.instant(), databaseInstant).abs()
    val driftMessage = "Hibernate: host and ${config.database} database " +
        "clocks have drifted ${delta.seconds}s apart"

    return when {
      delta > CLOCK_SKEW_UNHEALTHY_THRESHOLD -> {
        HealthStatus.unhealthy(driftMessage)
      }
      delta > CLOCK_SKEW_WARN_THRESHOLD -> {
        logger.warn { driftMessage }
        HealthStatus.healthy(driftMessage)
      }
      else ->
        HealthStatus.healthy("Hibernate: ${config.database} database")
    }
  }

  companion object {
    val CLOCK_SKEW_WARN_THRESHOLD = Duration.ofSeconds(10)
    val CLOCK_SKEW_UNHEALTHY_THRESHOLD = Duration.ofSeconds(30)
  }
}
