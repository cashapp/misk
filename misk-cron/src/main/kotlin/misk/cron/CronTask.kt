package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CronTask @Inject constructor() : AbstractIdleService() {
  @Inject private lateinit var cronClient: CronManager
  @Inject @ForMiskCron private lateinit var taskQueue: RepeatedTaskQueue

  override fun startUp() {
    logger.info { "Starting CronTask" }

    taskQueue.scheduleWithBackoff(INTERVAL) {
      cronClient.runReadyCrons()
      Status.OK
    }
  }

  override fun shutDown() {
    logger.info { "Stopping CronTask" }
    cronClient.removeAllCrons()
  }

  companion object {
    private val INTERVAL = Duration.ofSeconds(60L)

    private val logger = getLogger<CronTask>()
  }
}
