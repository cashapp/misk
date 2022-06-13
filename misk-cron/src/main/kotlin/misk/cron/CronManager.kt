package misk.cron

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.google.common.util.concurrent.ServiceManager
import wisp.logging.getLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CronManager @Inject constructor() {
  @Inject private lateinit var clock: Clock
  @Inject private lateinit var serviceManagerProvider: Provider<ServiceManager>
  @Inject @ForMiskCron private lateinit var executorService: ExecutorService
  @Inject @ForMiskCron private lateinit var zoneId: ZoneId

  private val runningCrons = mutableListOf<CompletableFuture<*>>()

  data class CronEntry(
    val name: String,
    val cronTab: String,
    val executionTime: ExecutionTime,
    val runnable: Runnable
  )

  private val cronEntries = mutableMapOf<String, CronEntry>()

  internal fun addCron(name: String, crontab: String, cron: Runnable) {
    require(name.isNotEmpty()) { "Expecting a valid cron name" }
    require(cronEntries[name] == null) { "Cron $name is already registered" }
    logger.info { "Adding cron entry $name, crontab=$crontab" }

    val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    val executionTime = ExecutionTime.forCron(CronParser(cronDefinition).parse(crontab))
    val cronEntry = CronEntry(name, crontab, executionTime, cron)

    cronEntries[name] = cronEntry
  }

  internal fun removeAllCrons() {
    logger.info { "Removing all cron entries" }
    cronEntries.clear()
  }

  fun runReadyCrons(lastRun: Instant) {
    if (!serviceManagerProvider.get().isHealthy) {
      logger.info { "Skipping running ready crons since service manager is not yet healthy" }
      return
    }

    val now = clock.instant()
    val previousTime = ZonedDateTime.ofInstant(lastRun, zoneId)

    logger.info {
      "Last execution was at $previousTime, now=${ZonedDateTime.ofInstant(now, zoneId)}"
    }
    removeCompletedCrons()
    cronEntries.values.forEach { cronEntry ->
      val nextExecutionTime = cronEntry.executionTime.nextExecution(previousTime).orElseThrow()
        .withSecond(0)
        .withNano(0)

      if (nextExecutionTime.toInstant() <= now) {
        logger.info {
          "CronJob ${cronEntry.name} was ready at $nextExecutionTime"
        }
        runCron(cronEntry)
      }
    }
  }

  private fun removeCompletedCrons() {
    runningCrons.removeIf { it.isDone }
  }

  private fun runCron(cronEntry: CronEntry) {
    runningCrons.add(
      CompletableFuture.runAsync(
        {
          val name = cronEntry.name

          try {
            logger.info { "Executing cronjob $name" }
            cronEntry.runnable.run()
          } catch (t: Throwable) {
            logger.warn { "Exception on cronjob $name: ${t.stackTraceToString()}" }
          } finally {
            logger.info { "Executing cronjob $name complete" }
          }
        },
        executorService
      )
    )
  }

  fun waitForCronsComplete() {
    CompletableFuture.allOf(*runningCrons.toTypedArray()).join()
    removeCompletedCrons()
  }

  companion object {
    private val logger = getLogger<CronManager>()
  }
}
