package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import misk.clustering.weights.ClusterWeightProvider
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import wisp.lease.LeaseManager
import misk.logging.getLogger
import java.time.Clock
import java.time.Duration
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
internal class CronTask @Inject constructor() : AbstractIdleService() {
  @Inject private lateinit var clock: Clock
  @Inject private lateinit var cronManager: CronManager
  @Inject @ForMiskCron private lateinit var taskQueue: RepeatedTaskQueue
  @Inject private lateinit var clusterWeight: ClusterWeightProvider

  override fun startUp() {
    logger.info { "Starting CronTask" }
    var lastRun = clock.instant()
    taskQueue.scheduleWithBackoff(INTERVAL) {
      if (clusterWeight.get() == 0) {
        logger.info { "CronTask is running on a passive node. Skipping." }
        return@scheduleWithBackoff Status.OK
      }
      val now = clock.instant()
      cronManager.runReadyCrons(lastRun)
      lastRun = now
      Status.OK
    }
  }

  override fun shutDown() {
    logger.info { "Stopping CronTask" }
    cronManager.removeAllCrons()
  }

  companion object {
    val INTERVAL: Duration = Duration.ofSeconds(60L)

    private val logger = getLogger<CronTask>()
  }
}
