package misk.web.actions

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Duration
import java.time.Instant
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.logging.getLogger
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Result
import misk.tasks.Status
import misk.web.ReadinessRefreshQueue
import misk.web.WebConfig

@Singleton
internal class ReadinessCheckService
@Inject
constructor(
  private val config: WebConfig,
  private val clock: Clock,
  private val serviceManagerProvider: Provider<ServiceManager>,
  @JvmSuppressWildcards private val healthCheckProvider: Provider<List<HealthCheck>>,
  @ReadinessRefreshQueue private val taskQueue: RepeatedTaskQueue,
) : AbstractIdleService() {
  @Volatile
  var status: CachedStatus? = null
    private set

  init {
    require(config.readiness_refresh_interval_ms < config.readiness_max_age_ms) {
      "readiness_refresh_interval_ms must be less than readiness_max_age_ms"
    }
  }

  override fun startUp() {
    logger.info { "starting readiness service" }

    taskQueue.schedule(Duration.ofMillis(0)) {
      refreshStatuses()
      Result(Status.OK, Duration.ofMillis(config.readiness_refresh_interval_ms.toLong()))
    }
  }

  override fun shutDown() {
    logger.info { "stopping readiness service" }

    status = null
  }

  @VisibleForTesting
  internal fun refreshStatuses() {
    val servicesNotRunning = serviceManagerProvider.get().servicesByState().values().asList().filterNot { it.isRunning }

    for (service in servicesNotRunning) {
      logger.info("Service not running: $service")
    }

    // Don't do healthchecks if services haven't all started. The app isn't in a good state yet,
    // and a health check could end up triggering random errors that we don't want to flood the
    // logs with.
    if (!servicesNotRunning.isEmpty()) return

    val statuses = healthCheckProvider.get().map { it.status() }

    // Get time AFTER health checks have completed
    val lastUpdate = clock.instant()
    status = CachedStatus(lastUpdate, statuses)
  }

  data class CachedStatus(val lastUpdate: Instant, val statuses: List<HealthStatus>)

  companion object {
    private val logger = getLogger<ReadinessCheckService>()
  }
}
