package misk.jobqueue

import misk.time.FakeClock
import misk.tokens.TokenGenerator
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
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
  clock: FakeClock,
  jobHandlers: Provider<Map<QueueName, JobHandler>>,
  tokenGenerator: TokenGenerator
) : FakeJobQueue(clock, jobHandlers, tokenGenerator)