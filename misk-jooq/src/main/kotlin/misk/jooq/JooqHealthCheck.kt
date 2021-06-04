package misk.jooq

import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import org.jooq.impl.DSL.now
import wisp.logging.getLogger
import java.time.Clock
import java.time.Duration
import javax.inject.Provider
import kotlin.reflect.KClass

class JooqHealthCheck(
  private val qualifier: KClass<out Annotation>,
  private val dataSourceProvider: Provider<out Service>,
  private val jooqTransacterProvider: Provider<JooqTransacter>,
  private val clock: Clock
) : HealthCheck {
  override fun status(): HealthStatus {
    val state = dataSourceProvider.get().state()
    if (state != Service.State.RUNNING) {
      return HealthStatus.unhealthy(
        "Jooq: ${qualifier.simpleName} database service is $state"
      )
    }

    val databaseInstant = try {
      val jooqTransacter = jooqTransacterProvider.get()
      jooqTransacter.transaction { (ctx) ->
        ctx.select(now()).fetchOne { it.component1().toInstant() }
      }
    } catch (exception: Exception) {
      log.error(exception) { "Jooq: error performing jooq health check" }
      return HealthStatus.unhealthy(
        "Jooq: failed to query ${qualifier.simpleName} database"
      )
    }

    val delta = Duration.between(clock.instant(), databaseInstant).abs()
    val driftMessage = "Jooq: host and ${qualifier.simpleName} database " +
      "clocks have drifted ${delta.seconds}s apart"

    return when {
      delta > CLOCK_SKEW_UNHEALTHY_THRESHOLD -> {
        HealthStatus.unhealthy(driftMessage)
      }
      delta > CLOCK_SKEW_WARN_THRESHOLD -> {
        log.warn { driftMessage }
        HealthStatus.healthy(driftMessage)
      }
      else ->
        HealthStatus.healthy("Jooq: ${qualifier.simpleName} database")
    }
  }

  companion object {
    val log = getLogger<JooqHealthCheck>()
    val CLOCK_SKEW_WARN_THRESHOLD: Duration = Duration.ofSeconds(10)
    val CLOCK_SKEW_UNHEALTHY_THRESHOLD: Duration = Duration.ofSeconds(30)
  }
}
