package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.getLogger
import org.hibernate.Session
import org.hibernate.SessionFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
  private val clock: Clock,
  private val config: DataSourceConfig
) : HealthCheck {

  override fun status(): HealthStatus {
    val state = serviceProvider.get().state()
    if (state != Service.State.RUNNING) {
      return HealthStatus.unhealthy("Hibernate: ${qualifier.simpleName} database service is $state")
    }

    val databaseInstant = try {
      selectNow()
    } catch (e: Exception) {
      if (config.type == DataSourceType.VITESS_MYSQL && config.database == "@master") {
        logger.warn("ping master database unsuccessful, trying to ping the replica")
        selectNowReplica()
      } else {
        logger.error(e) { "error performing hibernate health check" }
      }
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

  private fun selectNow(): Instant? {
    val sessionFactory = sessionFactoryProvider.get()
    return sessionFactory.openSession().use { session ->
      selectNow(session)
    }
  }

  private fun selectNowReplica(): Instant? {
    val sessionFactory = sessionFactoryProvider.get()
    return sessionFactory.openSession().use { session ->
      // Switch to replicas
      session.createNativeQuery("USE @replica").executeUpdate()
      try {
        selectNow(session)
      } finally {
        // Reset targetting
        session.createNativeQuery("USE").executeUpdate()
      }
    }
  }

  private fun selectNow(session: Session) =
      (session.createNativeQuery("SELECT NOW()").uniqueResult() as Timestamp).toInstant()

  companion object {
    val logger = getLogger<HibernateHealthCheck>()
    val CLOCK_SKEW_WARN_THRESHOLD: Duration = Duration.ofSeconds(10)
    val CLOCK_SKEW_UNHEALTHY_THRESHOLD: Duration = Duration.ofSeconds(30)
  }
}
