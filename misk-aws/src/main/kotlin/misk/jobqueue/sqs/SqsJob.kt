package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.SendMessageRequest
import misk.jobqueue.Job
import misk.jobqueue.QueueName

internal class SqsJob(
  override val queueName: QueueName,
  private val queues: QueueResolver,
  private val metrics: SqsMetrics,
  private val message: Message,
    // TODO: remove after this change has been deployed everywhere. This handles the case of existing
    // queued jobs during the deploy.
  randomIdempotenceKey: String
) : Job {
  override val body: String = message.body
  override val id: String = message.messageId
  override val idempotenceKey =
      message.messageAttributes[IDEMPOTENCY_KEY_ATTR]?.stringValue ?: randomIdempotenceKey
  override val attributes: Map<String, String> = message.messageAttributes
      .map { (key, value) -> key to value.stringValue }
      .filter { (key, _) -> key != IDEMPOTENCY_KEY_ATTR }
      .toMap()

  private val queue: ResolvedQueue = queues[queueName]

  override fun acknowledge() {
    queue.call { it.deleteMessage(queue.url, message.receiptHandle) }
    metrics.jobsAcknowledged.labels(queueName.value).inc()
  }

  override fun deadLetter() {
    if (queueName.isDeadLetterQueue) return

    val dlq = queues[queueName.deadLetterQueue]
    dlq.call { client ->
      client.sendMessage(SendMessageRequest()
          .withQueueUrl(dlq.url)
          .withMessageBody(body)
          .withMessageAttributes(message.messageAttributes))
    }
    queue.call { it.deleteMessage(queue.url, message.receiptHandle) }
    metrics.jobsDeadLettered.labels(queueName.value).inc()
  }

  companion object {
    const val ORIGINAL_TRACE_ID_ATTR = "x-original-trace-id"
    const val IDEMPOTENCY_KEY_ATTR = "idempotencey_key"
  }
}
