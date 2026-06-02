package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Duration
import misk.clustering.weights.ClusterWeightProvider
import misk.inject.AsyncSwitch
import misk.logging.getLogger
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status

@Singleton
internal class CronTask @Inject constructor() : AbstractIdleService() {
  @Inject private lateinit var clock: Clock
  @Inject private lateinit var cronManager: CronManager
  @Inject @ForMiskCron private lateinit var taskQueue: RepeatedTaskQueue
  @Inject private lateinit var clusterWeight: ClusterWeightProvider
  @Inject private lateinit var asyncSwitch: AsyncSwitch

  override fun startUp() {
    logger.info { "Starting CronTask" }
    var lastRun = clock.instant()
    var wasDisabled = false
    taskQueue.scheduleWithBackoff(INTERVAL) {
      when {
        asyncSwitch.isDisabled("cron") -> {
          if (!wasDisabled) {
            logger.info { "Async cron tasks disabled. Pausing." }
            wasDisabled = true
          }
          Status.OK
        }

        clusterWeight.get() == 0 -> {
          logger.info { "CronTask is running on a passive node. Skipping." }
          return@scheduleWithBackoff Status.OK
        }

        else -> {
          if (wasDisabled) {
            logger.info { "Async cron tasks re-enabled. Resuming." }
            wasDisabled = false
          }
          val now = clock.instant()
          cronManager.runReadyCrons(lastRun)
          lastRun = now
          Status.OK
        }
      }
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
