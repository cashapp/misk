package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractScheduledService
import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.jobqueue.Job
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.tokens.TokenGenerator
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class FakeAwsSqsModule : KAbstractModule() {
  override fun configure() {
    bind<JobQueue>().to<FakeSqs>()
    bind<JobConsumer>().to<FakeSqs>()
    multibind<Service>().to<FakeSqs>()
  }
}

class FakeJob(
  override val queueName: QueueName,
  override val id: String,
  override val body: String,
  override val attributes: Map<String, String>
) : Job {
  var acknowledged: Boolean = false
  var deadLettered: Boolean = false
  override fun acknowledge() {
    acknowledged = true
  }

  override fun deadLetter() {
    deadLettered = true
  }
}

/**
 * Fake for testing and development. Doesn't handle [misk.jobqueue.TransactionalJobQueue]s
 */

@Singleton
class FakeSqs @Inject internal constructor(
  private val tokenGenerator: TokenGenerator
) : AbstractScheduledService(), JobQueue, JobConsumer {

  val handlers = mutableMapOf<QueueName, JobHandler>()
  private val jobs = mutableSetOf<FakeJob>()
  private val acknowledgedJobs = mutableSetOf<FakeJob>()
  private val deadLetterJobs = mutableSetOf<FakeJob>()

  private var nextEnqueueTest: Pair<QueueName, String>? = null

  override fun runOneIteration() {
    // Remove all acknowledged and deadletter Jobs
    acknowledgedJobs += jobs.filter { it.acknowledged }
    deadLetterJobs += jobs.filter { it.deadLettered }
    jobs.removeIf { it.acknowledged || it.deadLettered }

    jobs.forEach {
      handlers[it.queueName]!!.handleJob(it)
    }
  }

  /**
   * Call the job handlers on this schedule. There is no sense of a visibility timeout in this fake.
   */
  override fun scheduler(): Scheduler = Scheduler.newFixedRateSchedule(1, 1, TimeUnit.SECONDS)

  /**
   * This method doesn't actually care about the duration. We can add this if needed.
   */
  override fun enqueue(
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    if (nextEnqueueTest != null) {
      check(queueName == nextEnqueueTest!!.first && body == nextEnqueueTest!!.second) {
        "Unexpected job enqueued: expected $nextEnqueueTest received ${queueName to body}"
      }
      nextEnqueueTest = null
    }
    // Just call the handler.
    jobs += FakeJob(queueName, tokenGenerator.generate(), body, attributes)
  }

  override fun subscribe(queueName: QueueName, handler: JobHandler): JobConsumer.Subscription {
    handlers += queueName to handler
    return object : JobConsumer.Subscription {
      override fun close() {
        handlers.remove(queueName)
      }
    }
  }

  /**
   * Useful for testing to make sure that jobs are added to the correct [QueueName].
   */
  fun checkNextEnqueue(queueName: QueueName, body: String) {
    nextEnqueueTest = queueName to body
  }
}
