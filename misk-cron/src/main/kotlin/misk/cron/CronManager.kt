package misk.cron

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.cron.CronManager.CronEntry.ExecutionTimeMetadata.Companion.toMetadata
import misk.logging.getLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.jvm.optionals.getOrNull

@Singleton
class CronManager @Inject constructor() {
  @Inject private lateinit var clock: Clock
  @Inject @ForMiskCron private lateinit var executorService: ExecutorService
  @Inject @ForMiskCron private lateinit var zoneId: ZoneId
  @Inject @ForMiskCron private lateinit var cronCoordinator: CronCoordinator

  private val runningCrons = mutableListOf<RunningCronEntry>()
  internal fun getRunningCrons() = runningCrons.toList()

  data class RunningCronEntry(
    val completableFuture: CompletableFuture<*>,
    val entry: CronEntry,
  ) {
    internal data class Metadata(
      val completableFuture: String,
      val entry: CronEntry.Metadata,
    )

    internal fun toMetadata() = Metadata(
      completableFuture = completableFuture.toString(),
      entry = entry.toMetadata(),
    )
  }

  data class CronEntry(
    val name: String,
    val cronTab: String,
    val executionTime: ExecutionTime,
    val runnable: Runnable
  ) {
    internal data class ExecutionTimeMetadata(
      val nextExecution: String?,
      val timeToNextExecution: String?,
      val lastExecution: String?,
      val timeFromLastExecution: String?
    ) {
      companion object {
        fun ExecutionTime.toMetadata(): ExecutionTimeMetadata {
          val now = ZonedDateTime.now()
          val nextExecution = nextExecution(now)
          val timeToNextExecution = timeToNextExecution(now)
          val lastExecution = lastExecution(now)
          val timeFromLastExecution = timeFromLastExecution(now)
          return ExecutionTimeMetadata(
            nextExecution = nextExecution.getOrNull().toString(),
            timeToNextExecution = timeToNextExecution.getOrNull().toString(),
            lastExecution = lastExecution.getOrNull().toString(),
            timeFromLastExecution = timeFromLastExecution.getOrNull().toString(),
          )
        }
      }
    }

    internal data class Metadata(
      val name: String,
      val cronTab: String,
      val executionTime: ExecutionTimeMetadata,
      val runnable: String
    )

    internal fun toMetadata() = Metadata(
      name = name,
      cronTab = cronTab,
      executionTime = executionTime.toMetadata(),
      runnable = runnable.toString(),
    )
  }

  private val cronEntries = mutableMapOf<String, CronEntry>()
  internal fun getCronEntries() = cronEntries.toMap()

  internal fun getMetadata() = CronData(
    cronEntries = cronEntries.mapValues { it.value.toMetadata() },
    runningCrons = runningCrons.map { it.toMetadata() },
  )

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

      if (nextExecutionTime.toInstant() <= now && cronCoordinator.shouldRunTask(cronEntry.name)) {
        logger.info {
          "CronJob ${cronEntry.name} was ready at $nextExecutionTime"
        }
        runCron(cronEntry)
      }
    }
  }

  private fun removeCompletedCrons() {
    runningCrons.removeIf { it.completableFuture.isDone }
  }

  internal fun runCron(cronEntry: CronEntry) {
    runningCrons.add(
      RunningCronEntry(
        completableFuture = CompletableFuture.runAsync(
          {
            val name = cronEntry.name

            try {
              logger.info { "Executing cronjob $name" }
              cronEntry.runnable.run()
            } catch (t: Throwable) {
              logger.error { "Exception on cronjob $name: ${t.stackTraceToString()}" }
            } finally {
              logger.info { "Executing cronjob $name complete" }
            }
          },
          executorService,
        ),
        entry = cronEntry,
      ),
    )
  }

  fun waitForCronsComplete() {
    CompletableFuture.allOf(*runningCrons.map { it.completableFuture }.toTypedArray()).join()
    removeCompletedCrons()
  }

  companion object {
    private val logger = getLogger<CronManager>()
  }
}
