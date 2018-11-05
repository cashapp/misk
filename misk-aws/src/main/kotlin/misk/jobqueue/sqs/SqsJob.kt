package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.SendMessageRequest
import misk.jobqueue.Job
import misk.jobqueue.QueueName

internal class SqsJob(
  override val queueName: QueueName,
  private val queueUrls: QueueUrlMapping,
  private val sqs: AmazonSQS,
  private val metrics: SqsMetrics,
  private val message: Message
) : Job {
  override val body: String = message.body
  override val id: String = message.messageId
  override val attributes: Map<String, String> = message.messageAttributes.map { (key, value) ->
    key to value.stringValue
  }.toMap()

  override fun acknowledge() {
    sqs.deleteMessage(queueUrls[queueName], message.receiptHandle)
    metrics.jobsAcknowledged.labels(queueName.value).inc()
  }

  override fun deadLetter() {
    if (queueName.isDeadLetterQueue) return

    sqs.sendMessage(SendMessageRequest()
        .withQueueUrl(queueUrls[queueName.deadLetterQueue])
        .withMessageBody(body)
        .withMessageAttributes(message.messageAttributes))
    sqs.deleteMessage(queueUrls[queueName], message.receiptHandle)
    metrics.jobsDeadLettered.labels(queueName.value).inc()
  }

  companion object {
    const val ORIGINAL_TRACE_ID_ATTR = "x-original-trace-id"
    const val APPROX_RECEIVE_COUNT_ATTR = "ApproximateReceiveCount"
  }
}