package misk.jobqueue

import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.hibernate.Gid
import misk.hibernate.Session
import misk.logging.getLogger
import misk.tokens.TokenGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.PriorityBlockingQueue
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * A fake implementation of [JobQueue] and [FakeTransactionalJobQueue] intended for testing.
 *
 * A [FakeJobQueue] adds helper methods to inspect the jobs in the queue and to trigger processing.
 *
 * Example usage might look like this:
 * ```
 * callApiThatEnqueuesJob()
 * assertJobIsCorrect(fakeJobQueue.peekJobs(MY_QUEUE).first())
 *
 * fakeJobQueue.handleJobs()
 * assertJobSideEffects()
 * ```
 */
@Singleton
class FakeJobQueue @Inject constructor(
  private val clock: Clock,
  private val jobHandlers: Provider<Map<QueueName, JobHandler>>,
  private val tokenGenerator: TokenGenerator
) : JobQueue, TransactionalJobQueue {
  private val jobQueues = ConcurrentHashMap<QueueName, PriorityBlockingQueue<FakeJob>>()
  private val deadletteredJobs = ConcurrentHashMap<QueueName, ConcurrentLinkedDeque<FakeJob>>()

  override fun enqueue(
    session: Session,
    gid: Gid<*, *>,
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    enqueue(session, queueName, body, idempotenceKey, deliveryDelay, attributes)
  }

  override fun enqueue(
    session: Session,
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    session.onPostCommit {
      enqueue(queueName, body, idempotenceKey, deliveryDelay, attributes)
    }
  }

  override fun enqueue(
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    val id = tokenGenerator.generate("fakeJobQueue")
    val availableAt =
        if (deliveryDelay == null) clock.instant() else clock.instant().plus(deliveryDelay)
    val job =
        FakeJob(queueName, id, idempotenceKey, body, attributes, 0, availableAt, deliveryDelay)
    jobQueues.getOrPut(queueName, ::PriorityBlockingQueue).add(job)
  }

  fun peekJobs(queueName: QueueName): List<Job> {
    val jobs = jobQueues[queueName]
    return jobs?.sortedBy { it.id } ?: listOf()
  }

  fun peekDeadlettered(queueName: QueueName): List<Job> {
    val jobs = deadletteredJobs[queueName]
    return jobs?.toList() ?: listOf()
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int = 1,
    visibilityTimeout: Duration? = null,
    considerDelays: Boolean = false
  ): List<FakeJob> {
    val jobs = jobQueues[queueName] ?: return listOf()
    return processJobs(assertAcknowledged, retries, visibilityTimeout, false) {
      pollNextJob(jobs, considerDelays)
    }
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    assertAcknowledged: Boolean = true,
    retries: Int = 1,
    visibilityTimeout: Duration? = null,
    considerDelays: Boolean = false
  ): List<FakeJob> {
    val result = mutableListOf<FakeJob>()
    for (queueName in jobQueues.keys) {
      result += handleJobs(queueName, assertAcknowledged, retries, visibilityTimeout,
          considerDelays)
    }
    return result
  }

  /** Returns true if job was handled. */
  fun handleJob(
    job: Job,
    assertAcknowledged: Boolean = true,
    retries: Int = 1,
    visibilityTimeout: Duration? = null
  ): Boolean {
    return processJobs(assertAcknowledged, retries, visibilityTimeout, false) {
      if (jobQueues[job.queueName]?.remove(job) == true) job as FakeJob else null
    }.isNotEmpty()
  }

  /** Returns all jobs that were handled. */
  fun reprocessDeadlettered(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): List<FakeJob> {
    val jobs = deadletteredJobs[queueName] ?: return listOf()
    return processJobs(assertAcknowledged, retries, null, true) {
      jobs.poll()
    }
  }

  /** Returns true if job was handled. */
  fun reprocessDeadlettered(
    job: Job,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): Boolean {
    return processJobs(assertAcknowledged, retries, null, true) {
      if (deadletteredJobs[job.queueName]?.remove(job) == true) job as FakeJob else null
    }.isNotEmpty()
  }

  /** jobsSupplier must remove jobs from the underlying deque. */
  private fun processJobs(
    assertAcknowledged: Boolean,
    retries: Int,
    visibilityTimeout: Duration?,
    deadletter: Boolean,
    jobsSupplier: () -> FakeJob?
  ): List<FakeJob> {
    val jobHandlers = jobHandlers.get()
    val result = mutableListOf<FakeJob>()
    // Used to prevent an infinite loop by mistake in supplier.
    val touchedJobs = IdentityHashMap<FakeJob, Boolean>()
    while (true) {
      val job = jobsSupplier.invoke() ?: break
      check(touchedJobs.put(job, true) == null)

      if (deadletter) {
        require(visibilityTimeout == null)
        // If we don't reset whether it's deadlettered we'll always add it back to the queue.
        job.deadLettered = false
        job.acknowledged = false
      }

      val jobHandler = jobHandlers[job.queueName]!!
      var errorThrown = false
      try {
        if (visibilityTimeout == null) {
          // Old style without visibility timeouts.
          retry(retries, FlatBackoff(Duration.ofMillis(20))) { jobHandler.handleJob(job) }
        } else {
          jobHandler.handleJob(job)
        }
      } catch (e: Throwable) {
        if (visibilityTimeout == null || deadletter) {
          deadletteredJobs.getOrPut(job.queueName, ::ConcurrentLinkedDeque).add(job)
          // Re-throwing also ensures that we won't cause an infinite loop for dlq reprocessing.
          throw e
        }
        // Ignore the error when using [visibilityTimeout].
        errorThrown = true
        log.error(e) { "Error while handling $job" }
      }

      result += job
      if (assertAcknowledged && !errorThrown) {
        check(job.acknowledged) { "Expected $job to be acknowledged after handling" }
      }
      if (visibilityTimeout != null && !job.acknowledged && !job.deadLettered) {
        reenqueue(job, retries, visibilityTimeout)
      }
    }

    // Reenqueue deadlettered jobs outside of the main loop to prevent an infinite loop.
    result.forEach { job ->
      if (job.deadLettered) {
        deadletteredJobs.getOrPut(job.queueName, ::ConcurrentLinkedDeque).add(job)
      }
    }
    return result
  }

  private fun reenqueue(
    job: FakeJob,
    retries: Int,
    visibilityTimeout: Duration
  ) {
    require(retries >= 1) { "[retries] must be >= 1" }
    if (job.attemptNumber + 1 >= retries) {
      log.info { "Retries ($retries) maxed out. Putting $job to its deadletter queue." }
      deadletteredJobs.getOrPut(job.queueName, ::ConcurrentLinkedDeque).add(job)
    } else {
      val newAttempt = job.newAttempt(clock.instant().plus(visibilityTimeout))
      log.info { "Job was not acknowledged and will be retried $newAttempt" }
      jobQueues.getOrPut(job.queueName, ::PriorityBlockingQueue).add(newAttempt)
    }
  }

  private fun pollNextJob(jobs: PriorityBlockingQueue<FakeJob>, considerDelays: Boolean): FakeJob? {
    if (!considerDelays) {
      return jobs.poll()
    }
    val now = clock.instant()!!
    while (true) {
      // [jobs] queue is sorted by [deliverAt].
      val job = jobs.peek()
      if (job == null || job.availableAt.isAfter(now)) {
        return null
      }
      // If remove() is false, then we lost race to another worker and should retry.
      if (jobs.remove(job)) {
        return job
      }
    }
  }

  companion object {
    private val log = getLogger<FakeJobQueue>()
  }
}

data class FakeJob(
  override val queueName: QueueName,
  override val id: String,
  override val idempotenceKey: String,
  override val body: String,
  override val attributes: Map<String, String>,
  var attemptNumber: Int,
  val availableAt: Instant,
  val deliveryDelay: Duration? = null
) : Job, Comparable<FakeJob> {
  var acknowledged: Boolean = false
    internal set
  var deadLettered: Boolean = false
    internal set

  override fun acknowledge() {
    acknowledged = true
  }

  override fun deadLetter() {
    deadLettered = true
  }

  fun newAttempt(newAvailableAt: Instant): FakeJob {
    return FakeJob(queueName, id, idempotenceKey, body, attributes, attemptNumber + 1,
        newAvailableAt)
  }

  override fun compareTo(other: FakeJob): Int {
    val result = availableAt.compareTo(other.availableAt)
    if (result == 0) {
      // FakeTokenGenerator generates tokens incrementally.
      return id.compareTo(other.id)
    }
    return result
  }
}
