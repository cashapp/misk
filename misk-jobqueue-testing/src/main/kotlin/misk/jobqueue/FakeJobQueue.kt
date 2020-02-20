package misk.jobqueue

import misk.tokens.TokenGenerator
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * A fake implementation of [JobQueue] intended for testing.
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
  private val jobHandlers: Provider<Map<QueueName, JobHandler>>,
  private val tokenGenerator: TokenGenerator
) : JobQueue {
  private val jobQueues = ConcurrentHashMap<QueueName, ConcurrentLinkedDeque<FakeJob>>()

  override fun enqueue(
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    val id = tokenGenerator.generate("fakeJobQueue")
    val job = FakeJob(queueName, id, body, attributes)
    jobQueues.getOrPut(queueName, ::ConcurrentLinkedDeque).add(job)
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

data class FakeJob(
  override val queueName: QueueName,
  override val id: String,
  override val body: String,
  override val attributes: Map<String, String>,
  internal var acknowledged: Boolean = false
) : Job {
  override fun acknowledge() {
    acknowledged = true
  }
  override fun deadLetter() {}
}
