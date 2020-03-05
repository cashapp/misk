package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.squareup.moshi.Moshi
import misk.jobqueue.Job
import misk.jobqueue.QueueName
import misk.moshi.adapter
import misk.time.timed

internal class SqsJob(
  override val queueName: QueueName,
  private val queues: QueueResolver,
  private val metrics: SqsMetrics,
  private val moshi: Moshi,
  private val message: Message
) : Job {
  override val body: String = message.body
  override val id: String = message.messageId
  override val idempotenceKey: String by lazy {
    jobqueueMetadata[JOBQUEUE_METADATA_IDEMPOTENCE_KEY]
        ?: error("$JOBQUEUE_METADATA_IDEMPOTENCE_KEY not set in $JOBQUEUE_METADATA_ATTR")
  }
  override val attributes: Map<String, String> by lazy {
    message.messageAttributes
        .filter { (key, _) -> key != JOBQUEUE_METADATA_ATTR }
        .map { (key, value) -> key to value.stringValue }.toMap()
  }

  private val queue: ResolvedQueue = queues.getForReceiving(queueName)
  private val jobqueueMetadata: Map<String, String> by lazy {
    val metadata = message.messageAttributes[JOBQUEUE_METADATA_ATTR]
    // NB: old/in-flight jobs enqueued prior to the introduction of _jobqueue-metadata won't have this property. In
    // order to not change the contract later on (optional to non-optional), synthesize a reasonable replacement.
    if (metadata == null) {
      // TODO(bruno): drop fallback; error out if metadata is not set.
      mapOf(
          JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
          // If the job had no metadata, there was no app-specified idempotence key;
          // any sufficiently random value would work, so just use system-assigned id.
          JOBQUEUE_METADATA_IDEMPOTENCE_KEY to id)
    } else {
      moshi.adapter<Map<String, String>>().fromJson(metadata.stringValue)!!
    }
  }

  override fun acknowledge() {
    deleteMessage(queue, message)
    metrics.jobsAcknowledged.labels(queueName.value, queueName.value).inc()
  }

  override fun deadLetter() {
    if (queueName.isDeadLetterQueue) return

    val dlq = queues.getForReceiving(queueName.deadLetterQueue)
    dlq.call { client ->
      client.sendMessage(SendMessageRequest()
          .withQueueUrl(dlq.url)
          .withMessageBody(body)
          // Preserves original jobqueue metadata and trace id.
          .withMessageAttributes(message.messageAttributes))
    }
    deleteMessage(queue, message)
    metrics.jobsDeadLettered.labels(queueName.value, queueName.value).inc()
  }

  private fun deleteMessage(queue: ResolvedQueue, message: Message) {
    val (deleteDuration, _) = queue.call {
      timed { it.deleteMessage(queue.url, message.receiptHandle) }
    }
    metrics.sqsDeleteTime.record(deleteDuration.toMillis().toDouble(), queueName.value, queueName.value)
  }

  companion object {
    /** Message attribute that captures original trace id for a job, when available. */
    const val ORIGINAL_TRACE_ID_ATTR = "x-original-trace-id"
    /** Message attribute used to store metadata specific to jobqueue functionality. JSON-encoded. */
    const val JOBQUEUE_METADATA_ATTR = "_jobqueue-metadata"
    /**
     * The name of the queue the job was originally submitted to. Used when operating with a global dead-letter queue,
     * so that jobs can be returned to their original (or retry) queue when reprocessing.
     */
    const val JOBQUEUE_METADATA_ORIGIN_QUEUE = "origin_queue"
    /** Client-assigned identifier, useful to detect duplicate messages. */
    const val JOBQUEUE_METADATA_IDEMPOTENCE_KEY = "idempotence_key"
  }
}
