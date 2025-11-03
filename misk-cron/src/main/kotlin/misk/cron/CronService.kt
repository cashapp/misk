package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Injector
import misk.logging.getLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
internal class CronService @Inject constructor(
  private val injector: Injector
) : AbstractIdleService() {
  @Inject private lateinit var cronManager: CronManager
  @Inject private lateinit var cronRunnableEntries: List<CronRunnableEntry>

  override fun startUp() {
    logger.info { "CronService started" }

    cronRunnableEntries.forEach { cronRunnable ->
      val name = cronRunnable.runnableClass.qualifiedName!!
      val annotations = cronRunnable.runnableClass.annotations
      val annotationPattern = annotations.find { it is CronPattern } as? CronPattern
      // Use the cron pattern specified in CronRunnableEntry if available.
      // If it's null, fall back to the pattern from the @CronPattern annotation on the class.
      // This allows the annotation pattern to serve as a default, while specifying
      // a cron pattern in CronRunnableEntry will override it if provided.
      val cronPattern = cronRunnable.cronPattern
        ?: annotationPattern
        ?: throw IllegalArgumentException("Expected $name to have @CronPattern specified")

      val runnable = injector.getProvider(cronRunnable.runnableClass.java).get()
      cronManager.addCron(name, cronPattern.pattern, runnable)
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
