package misk.hibernate

import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.logging.getLogger
import org.hibernate.SessionFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import kotlin.reflect.KClass

private val logger = getLogger<HibernateHealthCheck>()

/**
 * HealthCheck to confirm database connectivity and defend against clock skew.
 */
class HibernateHealthCheck(
  private val qualifier: KClass<out Annotation>,
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
      return HealthStatus.unhealthy("Hibernate: failed to query ${qualifier.simpleName} database")
    }

    val delta = Duration.between(clock.instant(), databaseInstant).abs()
    val driftMessage = "Hibernate: host and ${qualifier.simpleName} database " +
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
        HealthStatus.healthy("Hibernate: ${qualifier.simpleName} database")
    }
  }

  companion object {
    val CLOCK_SKEW_WARN_THRESHOLD = Duration.ofSeconds(10)
    val CLOCK_SKEW_UNHEALTHY_THRESHOLD = Duration.ofSeconds(30)
  }
}
