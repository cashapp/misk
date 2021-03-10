package misk.cron

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.squareup.moshi.Moshi
import misk.clustering.lease.LeaseManager
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.moshi.adapter
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CronManager @Inject constructor(
  moshi: Moshi,
  leaseManager: LeaseManager
) {
  @Inject private lateinit var clock: Clock
  @Inject private lateinit var jobQueue: JobQueue
  @Inject @ForMiskCron private lateinit var queueName: QueueName
  @Inject @ForMiskCron private lateinit var zoneId: ZoneId

  private val cronJobAdapter = moshi.adapter<CronJob>()

  data class CronEntry(
    val name: String,
    val cronTab: String,
    val executionTime: ExecutionTime,
    val runnable: Runnable
  )

  private val lease = leaseManager.requestLease(CRON_CLUSTER_LEASE_NAME)

  private val cronEntries = mutableMapOf<String, CronEntry>()

  data class CronQueueEntry(val executeAt: Instant, val cronEntry: CronEntry)

  private val cronQueueComparator: Comparator<CronQueueEntry> =
    compareBy { it.executeAt.toEpochMilli() }
  private val cronQueue = PriorityQueue(cronQueueComparator)

  fun addCron(name: String, crontab: String, cron: Runnable) {
    require(name.isNotEmpty()) { "Expecting a valid cron name" }
    require(cronEntries[name] == null) { "Cron $name is already registered" }
    logger.info { "Adding cron entry $name, crontab=$crontab" }

    val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    val executionTime = ExecutionTime.forCron(CronParser(cronDefinition).parse(crontab))
    val cronEntry = CronEntry(name, crontab, executionTime, cron)

    cronEntries[name] = cronEntry
    queueNextExecution(cronEntry)
  }

  internal fun removeAllCrons() {
    logger.info { "Removing all cron entries" }
    cronEntries.clear()
    cronQueue.clear()
  }

  private fun queueNextExecution(cronEntry: CronEntry) {
    val now = ZonedDateTime.ofInstant(clock.instant(), zoneId)
    val nextExecutionTime = cronEntry.executionTime.nextExecution(now).orElseThrow()
      .withSecond(0)
      .withNano(0)

    logger.info { "Next execution for cron ${cronEntry.name} is $nextExecutionTime" }
    cronQueue.add(CronQueueEntry(nextExecutionTime.toInstant(), cronEntry))
  }

  fun runReadyCrons() {
    val active = lease.checkHeld()
    val now = clock.instant()

    while (cronQueue.isNotEmpty()) {
      val cronQueueEntry = cronQueue.peek()
      if (cronQueueEntry.executeAt > now) break

      cronQueue.remove()
      val cronEntry = cronQueueEntry.cronEntry

      // Run the cron if we are the active lease holder, otherwise queue it up again.
      if (active) {
        logger.info {
          "CronJob ${cronEntry.name} was ready at " +
            "${ZonedDateTime.ofInstant(cronQueueEntry.executeAt, zoneId)}"
        }
        enqueueCronJob(cronEntry)
      } else {
        queueNextExecution(cronEntry)
      }
    }
  }

  private fun enqueueCronJob(cronEntry: CronEntry) {
    logger.info { "Enqueueing cronjob ${cronEntry.name}" }
    jobQueue.enqueue(
      queueName = queueName,
      body = cronJobAdapter.toJson(CronJob(cronEntry.name))
    )
  }

  internal fun runJob(name: String) {
    logger.info { "Executing cronjob $name" }
    val cronEntry = cronEntries[name] ?: return

    try {
      cronEntry.runnable.run()
    } catch (t: Throwable) {
      logger.warn { "Exception on cronjob $name: ${t.stackTraceToString()}" }
    } finally {
      logger.info { "Executing cronjob $name complete" }
      queueNextExecution(cronEntry)
    }
  }

  companion object {
    private val logger = getLogger<CronManager>()
    private const val CRON_CLUSTER_LEASE_NAME = "misk.cron.lease"
  }
}
