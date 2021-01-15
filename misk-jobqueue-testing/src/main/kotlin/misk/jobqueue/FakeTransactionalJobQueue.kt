package misk.jobqueue

import javax.inject.Inject
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
@Deprecated("Please use FakeJobQueue instead")
class FakeTransactionalJobQueue @Inject constructor(
  private val fakeJobQueue: FakeJobQueue
) : TransactionalJobQueue by fakeJobQueue {
  fun peekJobs(queueName: QueueName): List<Job> {
    return fakeJobQueue.peekJobs(queueName)
  }

  fun peekDeadlettered(queueName: QueueName): List<Job> {
    return fakeJobQueue.peekDeadlettered(queueName)
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): List<FakeJob> {
    return fakeJobQueue.handleJobs(queueName, assertAcknowledged, retries)
  }

  /** Returns all jobs that were handled. */
  fun reprocessDeadlettered(
    queueName: QueueName,
    assertAcknowledged: Boolean = true,
    retries: Int = 1
  ): List<FakeJob> {
    return fakeJobQueue.reprocessDeadlettered(queueName, assertAcknowledged, retries)
  }

  /** Returns all jobs that were handled. */
  fun handleJobs(assertAcknowledged: Boolean = true): List<FakeJob> {
    return fakeJobQueue.handleJobs(assertAcknowledged)
  }
}
