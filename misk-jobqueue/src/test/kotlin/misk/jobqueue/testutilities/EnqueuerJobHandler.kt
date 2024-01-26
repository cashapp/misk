package misk.jobqueue.testutilities

import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import misk.jobqueue.Job
import misk.jobqueue.JobHandler
import misk.jobqueue.JobQueue
import misk.moshi.adapter

internal class EnqueuerJobHandler @Inject private constructor(
  private val jobQueue: JobQueue,
  moshi: Moshi
) : JobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  override fun handleJob(job: Job) {
    jobQueue.enqueue(
      queueName = GREEN_QUEUE,
      body = jobAdapter.toJson(ExampleJob(color = Color.GREEN, message = "We made it!"))
    )
    job.acknowledge()
  }
}
