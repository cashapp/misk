package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.logging.getLogger

@Singleton
class ExampleHandler @Inject constructor() : SuspendingJobHandler {
  internal var counter = CountDownLatch(3) // Default to 3 for backward compatibility
  internal val jobs = CopyOnWriteArrayList<Job>()

  // Method to reset counter for specific test needs
  internal fun resetCounter(expectedJobs: Int) {
    counter = CountDownLatch(expectedJobs)
    jobs.clear()
  }

  override suspend fun handleJob(job: Job): JobStatus {
    logger.info { "Handling job $job, current counter $counter" }
    jobs.add(job) // Add job to list first
    counter.countDown() // Then signal completion
    return JobStatus.OK
  }

  companion object {
    val logger = getLogger<ExampleHandler>()
  }
}
