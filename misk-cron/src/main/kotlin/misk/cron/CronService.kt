package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import wisp.lease.LeaseManager
import wisp.logging.getLogger

@Singleton
internal class CronService @Inject constructor(
  private val injector: Injector,
) : AbstractIdleService() {
  @Inject private lateinit var cronManager: CronManager
  @Inject private lateinit var cronRunnableEntries: List<CronRunnableEntry>
  @Inject @ForMiskCron private lateinit var cronLeaseBehavior: CronLeaseBehavior

  override fun startUp() {
    logger.info { "CronService started" }

    cronRunnableEntries.forEach { cronRunnable ->
      val name = cronRunnable.runnableClass.qualifiedName!!
      val annotations = cronRunnable.runnableClass.annotations
      val cronPattern = annotations.find { it is CronPattern } as? CronPattern
        ?: throw IllegalArgumentException("Expected $name to have @CronPattern specified")

      val runnable = injector.getProvider(cronRunnable.runnableClass.java).get()
      cronManager.addCron(name, cronPattern.pattern, runnable)

      if (cronLeaseBehavior == CronLeaseBehavior.ONE_LEASE_PER_CRON) {
        // We want to request an interest in holding a lease as soon as the service starts up
        cronManager.buildTaskLease(cronRunnable.runnableClass)
      }
    }
  }

  override fun shutDown() {
    cronManager.removeAllCrons()
    logger.info { "CronService stopped" }
  }

  companion object {
    private val logger = getLogger<CronService>()
  }
}
