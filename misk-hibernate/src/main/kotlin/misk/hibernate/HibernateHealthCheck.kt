package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.logging.getLogger
import org.hibernate.SessionFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * HealthCheck to confirm database connectivity and defend against clock skew.
 */
internal class HibernateHealthCheck(
  private val qualifier: KClass<out Annotation>,
  // Lazily provide since the SessionFactory construction relies on Service startup.
  private val serviceProvider: Provider<out Service>,
  private val sessionFactoryProvider: Provider<SessionFactory>,
  private val clock: Clock
) : HealthCheck {

  override fun status(): HealthStatus {
    val state = serviceProvider.get().state()
    if (state != Service.State.RUNNING) {
      return HealthStatus.unhealthy("Hibernate: ${qualifier.simpleName} database service is $state")
    }

    val databaseInstant = try {
      val sessionFactory = sessionFactoryProvider.get()
      sessionFactory.openSession().use { session ->
        session.createNativeQuery("SELECT NOW()").uniqueResult() as Timestamp
      }.toInstant()
    } catch (e: Exception) {
      logger.error(e) { "error performing hibernate health check" }
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
    val logger = getLogger<HibernateHealthCheck>()
    val CLOCK_SKEW_WARN_THRESHOLD: Duration = Duration.ofSeconds(10)
    val CLOCK_SKEW_UNHEALTHY_THRESHOLD: Duration = Duration.ofSeconds(30)
  }
}
