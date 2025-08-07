package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.BlockingJobHandler
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import misk.logging.getLogger
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
  val queueConfig: SqsQueueConfig,
  val deadLetterQueueName: QueueName,
  val handler: JobHandler,
  val channel: Channel<SqsJob>,
  val client: SqsAsyncClient,
  val sqsQueueResolver: SqsQueueResolver,
  val sqsMetrics: SqsMetrics,
  val moshi: Moshi,
  val clock: Clock,
  val tracer: Tracer,
  val visibilityTimeoutCalculator: VisibilityTimeoutCalculator,
) {
  suspend fun run() {
    while (true) {
      val job = tracer.withSpan("channel-receive-queue-${queueName.value}") {
        channel.receive()
      }
      tracer.withSpan("process-queue-${queueName.value}") {
        val receiveFromChannelTimestamp = clock.millis()
        sqsMetrics.channelReceiveLag.labels(queueName.value)
          .observe((receiveFromChannelTimestamp - job.publishToChannelTimestamp).toDouble())
        val result = try {
          val startTime = clock.millis()
          val result = tracer.withSpan("handle-queue-${queueName.value}") {
            when (handler) {
              is SuspendingJobHandler -> handler.handleJob(job)
              is BlockingJobHandler -> runInterruptible {
                handler.handleJob(job)
              }
            }
          }
          sqsMetrics.handlerDispatchTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())
          result
        } catch (e: Exception) {
          sqsMetrics.handlerFailures.labels(queueName.value).inc()
          return@withSpan
        }
        when (result) {
          JobStatus.OK -> deleteMessage(job)
          JobStatus.DEAD_LETTER -> {
            deadLetterMessage(job)
            deleteMessage(job)
          }
          JobStatus.RETRY_WITH_BACKOFF -> retryWithBackoff(job)
          JobStatus.RETRY_LATER -> { /* no-op, will be retried after visibility timeout passes */
          }
        }
      }
    }
  }

  private suspend fun <T> Tracer.withSpan(spanName: String, block: suspend () -> T): T {
    val span = tracer.buildSpan(spanName).start()
    val scope = scopeManager().activate(span)
    try {
      return block()
    } catch (t: Throwable) {
      Tags.ERROR.set(span, true)
      throw t
    } finally {
      scope.close()
      span.finish()
    }
  }

  private suspend fun retryWithBackoff(job: SqsJob) {
    val visibilityTime = visibilityTimeoutCalculator.calculateVisibilityTimeout(
      currentReceiveCount = job.message.attributes()[MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT]?.toInt() ?: 1,
      queueVisibilityTimeout = queueConfig.visibility_timeout ?: 1
    )

    client.changeMessageVisibility(
      ChangeMessageVisibilityRequest.builder()
        .queueUrl(job.queueUrl)
        .receiptHandle(job.message.receiptHandle())
        .visibilityTimeout(visibilityTime)
        .build()
    ).await()

    sqsMetrics.visibilityTime.labels(queueName.value)
      .observe(visibilityTime.toDouble())
  }

  /**
   * Removes job from the queue.
   *
   * We may fail to acknowledge a job if visibility timeout is too short comparing to processing
   * time, and we get concurrent acknowledgments
   */
  private suspend fun deleteMessage(job: SqsJob) {
    val startTime = clock.millis()
    try {
      client.deleteMessage(
        DeleteMessageRequest.builder()
          .queueUrl(job.queueUrl)
          .receiptHandle(job.message.receiptHandle())
          .build()
      ).await()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to acknowledge job ${job.idempotenceKey} from queue ${job.queueName.value}"}
      sqsMetrics.jobsFailedToAcknowledge.labels(queueName.value).inc()
      return
    }
    sqsMetrics.sqsDeleteTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())
    sqsMetrics.jobsAcknowledged.labels(queueName.value).inc()
  }

  private suspend fun deadLetterMessage(job: SqsJob) {
    val deadLetterQueueUrl = sqsQueueResolver.getQueueUrl(deadLetterQueueName)
    val startTime = clock.millis()

    val response = client.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(deadLetterQueueUrl)
        .messageBody(job.body)
        .messageAttributes(job.message.messageAttributes())
        .build()
    ).await()
    sqsMetrics.sqsSendTime.labels(deadLetterQueueName.value).observe((clock.millis() - startTime).toDouble())
    sqsMetrics.jobsDeadLettered.labels(queueName.value).inc()
  }

  /**
   * Polls the messages from both the regular and the retry queue.
   */
  suspend fun poll() {
    if (queueConfig.install_retry_queue) {
      merge(messageFlow(queueName), messageFlow(queueName.retryQueue))
    } else {
      messageFlow(queueName)
    }.collect { received ->
      channel.send(received)
    }
  }

  private fun messageFlow(queueName: QueueName) = flow {
    val queueUrl = sqsQueueResolver.getQueueUrl(queueName)
    while (true) {
      val startTime = clock.millis()
      val response = fetchMessages(queueUrl).await()
      sqsMetrics.sqsReceiveTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())

      sqsMetrics.jobsReceived.labels(queueName.value).inc(response.messages().size.toDouble())
      response.messages().forEach { message ->
        message.attributes()[MessageSystemAttributeName.SENT_TIMESTAMP]?.let {
          val sentTimestamp = it.toLong()
          val processingLag = clock.instant().minusMillis(sentTimestamp).toEpochMilli().toDouble()
          val receiveCounter = message.attributes()[MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT]?.toInt()
          if (receiveCounter == 1) {
            sqsMetrics.queueFirstProcessingLag.labels(queueName.value).observe(processingLag)
          }
          sqsMetrics.queueProcessingLag.labels(queueName.value).observe(processingLag)
        }
        val publishToChannelTimestamp = clock.millis()
        emit(
          SqsJob(
            queueName = queueName,
            moshi = moshi,
            message = message,
            queueUrl = queueUrl,
            publishToChannelTimestamp = publishToChannelTimestamp
          )
        )
      }
    }
  }

  private fun fetchMessages(queueUrl: String): CompletableFuture<ReceiveMessageResponse> {
    val request = ReceiveMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageAttributeNames(MessageSystemAttributeName.ALL.toString())
      .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
      .maxNumberOfMessages(queueConfig.max_number_of_messages)
      .waitTimeSeconds(queueConfig.wait_timeout)
      .visibilityTimeout(queueConfig.visibility_timeout)
      .build()
    return client.receiveMessage(request)
  }

  companion object {
    val logger = getLogger<Subscriber>()
  }
}
