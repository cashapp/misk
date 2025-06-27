package misk.jobqueue.v2

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.jobqueue.QueueName
import misk.testing.FakeFixture
import misk.tokens.TokenGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.min

/**
 * A fake implementation of [JobEnqueuer] intended for testing.
 *
 * A [FakeJobEnqueuer] adds helper methods to inspect the jobs in the queue and to trigger processing.
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
class FakeJobEnqueuer @Inject constructor(
  private val clock: Clock,
  private val individualHandlers: Provider<Map<QueueName, JobHandler>>,
  private val tokenGenerator: TokenGenerator,
) : JobEnqueuer, FakeFixture() {
  private val jobQueues by resettable { ConcurrentHashMap<QueueName, PriorityBlockingQueue<FakeJob>>() }
  private val deadletteredJobs by resettable { ConcurrentHashMap<QueueName, ConcurrentLinkedDeque<FakeJob>>() }
  private val failureJobQueues by resettable { ConcurrentHashMap<QueueName, LinkedBlockingQueue<Exception>>() }
  private val failureJobQueue by resettable { LinkedBlockingQueue<Exception>() }

  /**
   * pushFailure is used to cause the next enqueue/batchEnqueue call to the job queue to throw.
   * When queueName is omitted, any enqueue will fail. Otherwise, only enqueues to the specific
   * queueName will throw the pushed exception.
   *
   * pushFailure can be used multiple times to queue up multiple failures
   */
  fun pushFailure(
    e: Exception,
    queueName: QueueName? = null
  ) {
    queueName?.let { failureJobQueues.getOrPut(it, ::LinkedBlockingQueue).add(e) } ?: failureJobQueue.add(e)
  }

  override suspend fun enqueue(
    queueName: QueueName,
    body: String,
    idempotencyKey: String?,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    enqueueAsync(queueName, body, idempotencyKey, deliveryDelay, attributes).await()
  }

  override fun enqueueBlocking(
    queueName: QueueName,
    body: String,
    idempotencyKey: String?,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    enqueueAsync(queueName, body, idempotencyKey, deliveryDelay, attributes).join()
  }

  override fun enqueueAsync(
    queueName: QueueName,
    body: String,
    idempotencyKey: String?,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ): CompletableFuture<Boolean> {
    throwIfQueuedFailure(queueName)
    val id = tokenGenerator.generate("fakeJobQueue")
    val resolvedIdempotencyKey = idempotencyKey ?: tokenGenerator.generate("fakeJobIdempotence")
    val job = FakeJob(queueName, id, resolvedIdempotencyKey, body, attributes, clock.instant(), deliveryDelay)
    jobQueues.getOrPut(queueName, ::PriorityBlockingQueue).add(job)
    return CompletableFuture.supplyAsync { true }
  }

  private fun nextFailure(queueName: QueueName?): Exception? {
    return queueName?.let { failureJobQueues.get(it)?.poll() } ?: failureJobQueue.poll()
  }

  @Throws
  private fun throwIfQueuedFailure(queueName: QueueName?) {
    nextFailure(queueName)?.let { throw(it) }
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
    considerDelays: Boolean = false
  ): List<FakeJob> {
    val jobs = jobQueues[queueName] ?: return listOf()
    return processJobs(assertAcknowledged, retries, false) {
      pollNextJob(jobs, considerDelays)
    }
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    assertAcknowledged: Boolean = true,
    considerDelays: Boolean = false
  ): List<FakeJob> {
    val result = mutableListOf<FakeJob>()
    for (queueName in jobQueues.keys) {
      result += handleJobs(queueName, assertAcknowledged, considerDelays = considerDelays)
    }
    return result
  }

  /** Returns true if job was handled. */
  fun handleJob(
    job: Job,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): Boolean {
    return processJobs(assertAcknowledged, retries, false) {
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
    return processJobs(assertAcknowledged, retries, true) {
      jobs.poll()
    }
  }

  /** Returns true if job was handled. */
  fun reprocessDeadlettered(
    job: Job,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): Boolean {
    return processJobs(assertAcknowledged, retries, true) {
      if (deadletteredJobs[job.queueName]?.remove(job) == true) job as FakeJob else null
    }.isNotEmpty()
  }

  /** jobsSupplier must remove jobs from the underlying deque. */
  private fun processJobs(
    assertAcknowledged: Boolean,
    retries: Int,
    deadletter: Boolean,
    jobsSupplier: () -> FakeJob?
  ): List<FakeJob> {
    val individualHandlers = individualHandlers.get()
    val resultedJobs = mutableListOf<FakeJob>()
    val jobsToQueueBack = mutableSetOf<FakeJob>()

    jobsSupplier.asJobBatchSequence().forEach { (queueName, jobs) ->
      if (deadletter) {
        // If we don't reset whether the job was deadlettered, it will be added back to the queue
        jobs.forEach { job ->
          job.deadLettered = false
          job.acknowledged = false
        }
      }

      val jobHandler = individualHandlers[queueName]!!
      try {
        val retryConfig = RetryConfig.Builder(retries, FlatBackoff(Duration.ofMillis(20)))
        retry(retryConfig.build()) {
          // we re-enqueue jobs if the backoff delayed time was called
          jobsToQueueBack.addAll(jobs.filter { it.delayedForBackoff })
          // we have to check which jobs in the batch were not processed in the previous try
          val validJobs = jobs.filter {
            !it.delayedForBackoff && !it.deadLettered && !it.acknowledged
          }

          if (validJobs.isNotEmpty()) {
            val job = validJobs.single()
            val jobStatus = when (jobHandler) {
              is SuspendingJobHandler -> runBlocking { jobHandler.handleJob(validJobs.single())}
              is BlockingJobHandler -> jobHandler.handleJob(validJobs.single())
              else -> error("Unknown job handler type $jobHandler")
            }
            when (jobStatus) {
              JobStatus.OK -> job.acknowledged = true
              JobStatus.DEAD_LETTER -> job.deadLettered = true
              JobStatus.RETRY_LATER -> job.acknowledged = false
              JobStatus.RETRY_WITH_BACKOFF -> {
                job.acknowledged = false
                job.delayedForBackoff = true
              }
            }
          }
        }
      } catch (e: Throwable) {
        deadletteredJobs.getOrPut(queueName, ::ConcurrentLinkedDeque)
          .addAll(jobs.filter { !it.acknowledged })
        throw e
      }

      // If a job was not added to jobsToQueueBack, the job has been handled.
      jobs.asSequence()
        .filter { !jobsToQueueBack.contains(it) }
        .forEach { job ->
          resultedJobs += job
          if (!job.deadLettered && assertAcknowledged && !job.acknowledged) {
            deadletteredJobs.getOrPut(queueName, ::ConcurrentLinkedDeque).add(job)
            error("Expected $job to be acknowledged after handling")
          }
        }
    }

    // Re-enqueue deadlettered jobs outside of the main loop to prevent an infinite loop.
    resultedJobs.forEach { job ->
      if (job.deadLettered) {
        deadletteredJobs.getOrPut(job.queueName, ::ConcurrentLinkedDeque).add(job)
      } else if (!job.acknowledged) {
        jobQueues.getOrPut(job.queueName, ::PriorityBlockingQueue).add(job)
      }
    }
    // Similarly to above re-enqueue jobs that have been called to have the visibility timeout.
    jobsToQueueBack.forEach { job ->
      if (job.delayedForBackoff && !job.acknowledged) {
        job.delayedForBackoff = false
        jobQueues.getOrPut(job.queueName, ::PriorityBlockingQueue).add(job)
      }
    }

    return resultedJobs
  }

  private fun pollNextJob(jobs: PriorityBlockingQueue<FakeJob>, considerDelays: Boolean): FakeJob? {
    if (!considerDelays) {
      return jobs.poll()
    }
    val now = clock.instant()!!
    while (true) {
      // [jobs] queue is sorted by [deliverAt].
      val job = jobs.peek()
      if (job == null
        || (job.deliveryDelay != null && job.deliverAt.isAfter(now))) {
        return null
      }
      // If remove() is false, then we lost race to another worker and should retry.
      if (jobs.remove(job)) {
        return job
      }
    }
  }

  /**
   * The sequence fetches jobs from the supplier and yields the jobs in batches. If the
   * corresponding queue's handler is JobHandler, the queue will yield a series of 1-job
   * batches. If the queue's handler is BatchJobHandler, the queue will yield one batch once
   * the supplier is exhausted. The batch will consist of all jobs from that queue.
   */
  private fun (() -> FakeJob?).asJobBatchSequence(): Sequence<Pair<QueueName, List<FakeJob>>> {
    val jobSupplier = this

    return sequence {
      // Used to prevent an infinite loop by mistake in supplier.
      val touchedJobs = mutableSetOf<FakeJob>()
      val batchedJobs = mutableMapOf<QueueName, MutableList<FakeJob>>()

      while (true) {
        val job = jobSupplier.invoke() ?: break
        check(touchedJobs.add(job))
        yield(job.queueName to listOf(job))
      }

      for ((queueName, jobs) in batchedJobs) {
        yield(queueName to jobs)
      }
    }
  }
}

data class FakeJob(
  override val queueName: QueueName,
  override val id: String,
  override val idempotenceKey: String,
  override val body: String,
  override val attributes: Map<String, String>,
  val enqueuedAt: Instant,
  var deliveryDelay: Duration? = null,
) : Job, Comparable<FakeJob> {
  val deliverAt: Instant by lazy {
    when (deliveryDelay) {
      null -> enqueuedAt
      else -> enqueuedAt.plus(deliveryDelay)
    }
  }
  var acknowledged: Boolean = false
    internal set
  var deadLettered: Boolean = false
    internal set

  var delayedForBackoff: Boolean = false
    internal set

  var delayDuration: Long? = null
    internal set

  fun delayWithBackoff() {
    delayedForBackoff  = true

    delayDuration = min((delayDuration ?: misk.jobqueue.FakeJob.MIN_DElAY_DURATION) * 2,
      misk.jobqueue.FakeJob.MAX_DELAY_DURATION
    )
    deliveryDelay = Duration.ofMillis(delayDuration!!)
  }

  override fun compareTo(other: FakeJob): Int {
    val result = deliverAt.compareTo(other.deliverAt)
    if (result == 0) {
      // FakeTokenGenerator generates tokens incrementally.
      return id.compareTo(other.id)
    }
    return result
  }
}
