package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import jakarta.inject.Singleton
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import wisp.logging.getLogger
import java.util.concurrent.CountDownLatch

@Singleton
class ExampleHandler @Inject constructor(): SuspendingJobHandler {
  internal val counter = CountDownLatch(3)
  internal val jobs = mutableListOf<Job>()

  override suspend fun handleJob(job: Job): JobStatus {
    logger.info { "Handling job $job, current counter $counter" }
    counter.countDown()
    jobs.add(job)
    return JobStatus.OK
  }

  companion object {
    val logger = getLogger<ExampleHandler>()
  }
}
