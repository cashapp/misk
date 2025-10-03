package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import jakarta.inject.Singleton
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.logging.getLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList

@Singleton
class ExampleHandler @Inject constructor(): SuspendingJobHandler {
  internal val counter = CountDownLatch(3)
  internal val jobs = CopyOnWriteArrayList<Job>()

  override suspend fun handleJob(job: Job): JobStatus {
    logger.info { "Handling job $job, current counter $counter" }
    jobs.add(job)        // Add job to list first
    counter.countDown()  // Then signal completion
    return JobStatus.OK
  }

  companion object {
    val logger = getLogger<ExampleHandler>()
  }
}
