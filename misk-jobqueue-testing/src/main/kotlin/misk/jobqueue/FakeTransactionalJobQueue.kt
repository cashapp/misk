package misk.jobqueue

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

  override fun enqueue(
    session: Session,
    gid: Gid<*, *>,
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    enqueue(session, queueName, body, deliveryDelay, attributes)
  }

  override fun enqueue(
    session: Session,
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    session.onPostCommit {
      val id = tokenGenerator.generate("fakeJobQueue")
      val job = FakeJob(queueName, id, body, attributes)
      jobQueues.getOrPut(queueName, ::ConcurrentLinkedDeque).add(job)
    }
  }

  fun peekJobs(queueName: QueueName): List<Job> {
    val jobs = jobQueues[queueName]
    return jobs?.toList() ?: listOf()
  }

  fun handleJobs(queueName: QueueName) {
    val jobHandler = jobHandlers.get()[queueName]!!
    val jobs = jobQueues[queueName] ?: return

    while (true) {
      val job = jobs.poll() ?: break
      jobHandler.handleJob(job)
      check(job.acknowledged) { "Expected $job to be acknowledged after handling" }
    }
  }

  fun handleJobs() = jobQueues.keys.forEach { handleJobs(it) }
}
