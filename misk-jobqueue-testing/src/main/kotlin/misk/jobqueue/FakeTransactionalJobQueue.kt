package misk.jobqueue

import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.hibernate.Gid
import misk.hibernate.Session
import misk.tokens.TokenGenerator
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * A fake implementation of [TransactionalJobQueue] intended for testing.
 *
 * A [FakeTransactionalJobQueue] adds helper methods to inspect the jobs in the queue and to
 * trigger processing.
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
class FakeTransactionalJobQueue @Inject constructor(
  private val jobHandlers: Provider<Map<QueueName, JobHandler>>,
  private val tokenGenerator: TokenGenerator
) : TransactionalJobQueue {
  private val jobQueues = ConcurrentHashMap<QueueName, ConcurrentLinkedDeque<FakeJob>>()
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
      val id = tokenGenerator.generate("fakeJobQueue")
      val job = FakeJob(
          queueName = queueName,
          id = id,
          idempotenceKey = idempotenceKey,
          body = body,
          attributes = attributes
      )
      jobQueues.getOrPut(queueName, ::ConcurrentLinkedDeque).add(job)
    }
  }

  fun peekJobs(queueName: QueueName): List<Job> {
    val jobs = jobQueues[queueName]
    return jobs?.toList() ?: listOf()
  }

  fun peekDeadlettered(queueName: QueueName): List<Job> {
    val jobs = deadletteredJobs[queueName]
    return jobs?.toList() ?: listOf()
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): List<FakeJob> {
    return processJobs(queueName, assertAcknowledged, retries, false)
  }

  /** Returns all jobs that were handled. */
  fun reprocessDeadlettered(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): List<FakeJob> {
    return processJobs(queueName, assertAcknowledged, retries, true)
  }

  private fun processJobs(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int,
    deadletter: Boolean
  ): List<FakeJob> {
    val jobHandler = jobHandlers.get()[queueName]!!
    val jobs = when (deadletter) {
      true -> deadletteredJobs[queueName] ?: return listOf()
      else -> jobQueues[queueName] ?: return listOf()
    }

    val result = mutableListOf<FakeJob>()
    while (true) {
      val job = jobs.poll() ?: break
      try {
        retry(retries, FlatBackoff(Duration.ofMillis(20))) { jobHandler.handleJob(job) }
      } catch (e: Throwable) {
        deadletteredJobs.getOrPut(queueName, ::ConcurrentLinkedDeque).add(job)
        throw e
      }

      result += job
      if (assertAcknowledged) {
        check(job.acknowledged) { "Expected $job to be acknowledged after handling" }
      }
    }

    return result
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(assertAcknowledged: Boolean = true): List<FakeJob> {
    val result = mutableListOf<FakeJob>()
    for (queueName in jobQueues.keys) {
      result += handleJobs(queueName, assertAcknowledged)
    }
    return result
  }
}
