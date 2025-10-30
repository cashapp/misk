package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import jakarta.inject.Singleton
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.logging.getLogger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

@Singleton
class ExampleExternalQueueHandler @Inject constructor(): SuspendingJobHandler {
  internal val counter = CountDownLatch(1)
  internal val jobs = CopyOnWriteArrayList<Job>()

  override suspend fun handleJob(job: Job): JobStatus {
    logger.info { "Handling external queue job $job, current counter $counter" }
    jobs.add(job)
    counter.countDown()
    return JobStatus.OK
  }

  companion object {
    val logger = getLogger<ExampleExternalQueueHandler>()
  }
}
