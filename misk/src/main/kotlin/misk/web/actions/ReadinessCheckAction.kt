package misk.web.actions

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ServiceManager
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.BindingAnnotation
import misk.healthchecks.HealthCheck
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebConfig
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import misk.healthchecks.HealthStatus
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val logger = getLogger<ReadinessCheckAction>()

@Singleton
class ReadinessCheckAction @Inject internal constructor(
  private val serviceManagerProvider: Provider<ServiceManager>,
  @JvmSuppressWildcards private val healthChecks: List<HealthCheck>,
  @ReadinessRefreshQueue private val taskQueue: RepeatedTaskQueue,
  val config: WebConfig,
  val clock: Clock
) : WebAction {

  private var cachedStatus: CachedStatus? = null

  private val refreshExecutor = ThreadPoolExecutor(
    // no concurrency on refresh
    1,
    1,
    config.readiness_max_age_ms.toLong(), TimeUnit.MILLISECONDS,
    SynchronousQueue(),
    ThreadFactoryBuilder()
      .setNameFormat("readiness-refresh-%d")
      .build()
  )

  @Get("/_readiness")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @AvailableWhenDegraded
  fun readinessCheck(): Response<String> {
    val servicesNotRunning = serviceManagerProvider.get().servicesByState().values().asList()
      .filterNot { it.isRunning }

    for (service in servicesNotRunning) {
      logger.info("Service not running: $service")
    }

    if (!servicesNotRunning.isEmpty()) {
      // Don't do healthchecks if services haven't all started. The app isn't in a good state yet,
      // and a health check could end up triggering random errors that we don't want to flood the
      // logs with.
      return Response("", statusCode = 503)
    }

    // Only null on first run
    val (lastUpdate, statuses) = cachedStatus
      // Block to get the initial status and enqueue background refresh
      ?: refreshStatuses().also {
        taskQueue.scheduleWithBackoff(Duration.ofMillis(config.readiness_refresh_interval_ms.toLong())) {
          refreshStatuses()
          Status.OK
        }
      }

    // This needs to be independent of the max age check so that services can recover
    // If refreshStatuses() is never run then a failed/stale check can never recover
    if (
      clock.instant().isAfter(lastUpdate.plusMillis(config.readiness_refresh_interval_ms.toLong()))
      // don't launch concurrent refreshes
      && refreshExecutor.activeCount == 0
    ) {
      // Launch in the background so that we can still return the cached state
      refreshExecutor.execute {
        refreshStatuses()
      }
    }

    if (clock.instant().isAfter(lastUpdate.plusMillis(config.readiness_max_age_ms.toLong()))) {
      logger.info("Failed health check: last status check is older than max age")
      return Response("", statusCode = 503)
    }

    val failedHealthChecks = statuses.filter { !it.isHealthy }

    if (failedHealthChecks.isEmpty()) {
      return Response("", statusCode = 200)
    }

    for (healthCheck in failedHealthChecks) {
      logger.info("Failed health check: ${healthCheck.messages}")
    }
    return Response("", statusCode = 503)
  }

  @VisibleForTesting
  internal fun refreshStatuses(): CachedStatus {
    val statuses = healthChecks.map { it.status() }
    // Get time AFTER health checks have completed
    val lastUpdate = clock.instant()
    cachedStatus = CachedStatus(lastUpdate, statuses)
    return cachedStatus as CachedStatus
  }

  data class CachedStatus(
    val lastUpdate: Instant,
    val statuses: List<HealthStatus>
  )
}

@BindingAnnotation
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ReadinessRefreshQueue
