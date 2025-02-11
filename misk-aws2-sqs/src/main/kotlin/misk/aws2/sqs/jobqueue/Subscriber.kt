package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import misk.jobqueue.QueueName
import misk.jobqueue.v2.BlockingJobHandler
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.Clock
import java.util.concurrent.CompletableFuture

/**
 * Subscriber reads jobs from the channel and passes them to handler.
 *
 * It responds to handler results by either acknowledging the job
 * or moving it to a dead letter queue.
 */
class Subscriber(
  val queueName: QueueName,
  val queueUrl: String,
  val client: SqsAsyncClient,
  val handler: JobHandler,
  val channel: Channel<SqsJob>,
  val retryQueueUrl: String,
  val deadLetterQueueName: QueueName,
  val sqsMetrics: SqsMetrics,
  val queueResolver: QueueResolver,
  val moshi: Moshi,
  val clock: Clock,
) {
  suspend fun run() {
    while (true) {
      val job = channel.receive()
      val receiveFromChannelTimestamp = clock.instant().nano
      sqsMetrics.channelReceiveLag.labels(queueName.value).observe((receiveFromChannelTimestamp - job.publishToChannelTimestamp).toDouble())
      val result = try {
        val timer = sqsMetrics.handlerDispatchTime.labels(queueName.value).startTimer()
        val result = when (handler) {
          is SuspendingJobHandler -> handler.handleJob(job)
          is BlockingJobHandler -> runInterruptible {
            handler.handleJob(job)
          }
        }
        timer.observeDuration()
        result
      } catch (e: Exception) {
        sqsMetrics.handlerFailures.labels(queueName.value).inc()
        continue
      }
      when (result) {
        JobStatus.OK -> deleteMessage(job)
        JobStatus.DEAD_LETTER -> {
          deadLetterMessage(job)
          deleteMessage(job)
        }
        JobStatus.RETRY_LATER -> { /* no-op, will be retried after visibility timeout passes */ }
      }
    }
  }

  private suspend fun deleteMessage(job: SqsJob) {
    val timer = sqsMetrics.sqsDeleteTime.labels(queueName.value).startTimer()
    client.deleteMessage(
      DeleteMessageRequest.builder()
        .queueUrl(job.queueUrl)
        .receiptHandle(job.message.receiptHandle())
        .build()
    ).await()
    timer.observeDuration()
    sqsMetrics.jobsAcknowledged.labels(queueName.value).inc()
  }

  private suspend fun deadLetterMessage(job: SqsJob) {
    val deadLetterQueueUrl = queueResolver.getQueueUrl(deadLetterQueueName)
    val timer = sqsMetrics.sqsSendTime.labels(deadLetterQueueName.value).startTimer()
    client.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(deadLetterQueueUrl)
        .messageBody(job.body)
        .build()
    ).await()
    timer.observeDuration()
    sqsMetrics.jobsDeadLettered.labels(queueName.value).inc()
  }

  /**
   * Polls the messages from both the regular and the retry queue.
   */
  suspend fun poll() {
    merge(
      messageFlow(queueName, queueUrl),
      messageFlow(queueName, retryQueueUrl)
    )
      .collect {
          received -> channel.send(received)
      }
  }

  private fun messageFlow(queueName: QueueName, queueUrl: String) = flow {
    while (true) {
      val timer = sqsMetrics.sqsReceiveTime.labels(queueName.value).startTimer()
      val response = fetchMessages(queueUrl).await()
      timer.observeDuration()

      sqsMetrics.jobsReceived.labels(queueName.value).inc(response.messages().size.toDouble())
      response.messages().forEach { message ->
        message.messageAttributes()[SQS_ATTRIBUTE_SENT_TIMESTAMP]?.let {
          val sentTimestamp = it.stringValue().toLong()
          val processingLag = clock.instant().minusMillis(sentTimestamp).toEpochMilli()
          sqsMetrics.queueProcessingLag.labels(queueName.value).observe(processingLag.toDouble())
        }
        val publishToChannelTimestamp = clock.instant()
        emit(
          SqsJob(
            queueName = queueName,
            moshi = moshi,
            message = message,
            queueUrl = queueUrl,
            publishToChannelTimestamp = publishToChannelTimestamp.toEpochMilli()
          )
        )
      }
    }
  }

  private fun fetchMessages(queueUrl: String): CompletableFuture<ReceiveMessageResponse> {
    // TODO configure batch size here
    val batchSize = 1
    val request = ReceiveMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageAttributeNames("All")
      .maxNumberOfMessages(batchSize)
      .build()
    return client.receiveMessage(request)
  }

  companion object {
    private const val SQS_ATTRIBUTE_SENT_TIMESTAMP = "SentTimestamp"
  }
}
