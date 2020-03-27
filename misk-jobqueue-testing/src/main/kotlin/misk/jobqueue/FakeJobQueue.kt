package misk.jobqueue

import misk.hibernate.Gid
import misk.hibernate.Session
import misk.time.FakeClock
import misk.tokens.TokenGenerator
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * A fake implementation of [JobQueue] and [TransactionalJobQueue] intended for testing.
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
open class FakeJobQueue @Inject constructor(
  private val fakeClock: FakeClock,
  private val jobHandlers: Provider<Map<QueueName, JobHandler>>,
  private val tokenGenerator: TokenGenerator
) : JobQueue, TransactionalJobQueue {
  private val jobQueues = ConcurrentHashMap<QueueName, PriorityBlockingQueue<FakeJob>>()

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
    val executeAt = fakeClock.instant().plus(deliveryDelay ?: Duration.ZERO)

    val job = FakeJob(
      queueName = queueName,
      id = id,
      idempotenceKey = idempotenceKey,
      body = body,
      attributes = attributes,
      executeAt = executeAt
    )
    jobQueues.getOrPut(queueName, ::PriorityBlockingQueue).add(job)
  }

  fun peekJobs(queueName: QueueName): List<Job> {
    val jobs = jobQueues[queueName]
    return jobs?.toList() ?: listOf()
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    executeImmediately: Boolean = true
  ): List<FakeJob> {
    val jobHandler = jobHandlers.get()[queueName]!!
    val jobs = jobQueues[queueName] ?: return listOf()

    val result = mutableListOf<FakeJob>()
    while (true) {
      val job = jobs.poll() ?: break
      if (!executeImmediately && job.executeAt.isAfter(fakeClock.instant())) {
        jobQueues.getOrPut(queueName, ::PriorityBlockingQueue).add(job)
        break
      }
      jobHandler.handleJob(job)
      result += job
      if (assertAcknowledged) {
        check(job.acknowledged) { "Expected $job to be acknowledged after handling" }
      }
    }

    return result
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    assertAcknowledged: Boolean = true,
    executeImmediately: Boolean = true
  ): List<FakeJob> {
    val result = mutableListOf<FakeJob>()
    for (queueName in jobQueues.keys) {
      result += handleJobs(queueName, assertAcknowledged, executeImmediately)
    }
    return result
  }
}

data class FakeJob(
  override val queueName: QueueName,
  override val id: String,
  override val idempotenceKey: String,
  override val body: String,
  override val attributes: Map<String, String>,
  val executeAt: Instant
) : Job, Comparable<FakeJob> {
  var acknowledged: Boolean = false
    private set
  var deadLettered: Boolean = false
    private set

  override fun acknowledge() {
    acknowledged = true
  }

  override fun deadLetter() {
    deadLettered = true
  }

  override operator fun compareTo(other: FakeJob): Int {
    return this.executeAt.compareTo(other.executeAt)
  }
}
