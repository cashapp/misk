package misk.jobqueue.testutilities

import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import misk.jobqueue.JobQueue
import misk.moshi.adapter
import java.time.Duration

internal class ExampleJobEnqueuer @Inject private constructor(
  private val jobQueue: JobQueue,
  moshi: Moshi
) {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  fun enqueueRed(message: String, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    val job = ExampleJob(Color.RED, message, hint)
    jobQueue.enqueue(
      RED_QUEUE, body = jobAdapter.toJson(job), deliveryDelay = deliveryDelay,
      attributes = mapOf("key" to "value")
    )
  }

  fun enqueueGreen(message: String, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    val job = ExampleJob(Color.GREEN, message, hint)
    jobQueue.enqueue(
      GREEN_QUEUE, body = jobAdapter.toJson(job), deliveryDelay = deliveryDelay,
      attributes = mapOf("key" to "value")
    )
  }

  fun batchEnqueueRed(messages: List<String>, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    jobQueue.batchEnqueue(RED_QUEUE, messages.map {
      JobQueue.JobRequest(
        body = jobAdapter.toJson(ExampleJob(Color.RED, it, hint)),
        deliveryDelay = deliveryDelay,
        attributes = mapOf("key" to "value")
      )
    })
  }

  fun enqueueEnqueuer() {
    jobQueue.enqueue(ENQUEUER_QUEUE, body = "")
  }
}
