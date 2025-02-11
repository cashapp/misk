package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import kotlinx.coroutines.future.await
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.parentQueue
import misk.jobqueue.v2.JobEnqueuer
import misk.moshi.adapter
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Singleton
class SqsJobEnqueuer @Inject constructor(
  private val client: SqsAsyncClient,
  private val queueResolver: QueueResolver,
  private val sqsMetrics: misk.aws2.sqs.jobqueue.SqsMetrics,
  private val moshi: Moshi,
) : JobEnqueuer<SendMessageResponse> {
  /**
   * Enqueue the job and suspend waiting for the confirmation
   */
  override suspend fun enqueue(
    queueName: QueueName,
    body: String,
    idempotencyKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ) {
    enqueueAsync(
      queueName = queueName,
      body = body,
      idempotencyKey = idempotencyKey,
      deliveryDelay = deliveryDelay,
      attributes = attributes
    ).await()
  }

  /**
   * Enqueue the job and block waiting for the confirmation.
   */
  override fun enqueueBlocking(
    queueName: QueueName,
    body: String,
    idempotencyKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ) {
    enqueueAsync(
      queueName = queueName,
      body = body,
      idempotencyKey = idempotencyKey,
      deliveryDelay = deliveryDelay,
      attributes = attributes
    ).join()
  }

  /**
   * Enqueue the job and return a CompletableFuture.
   *
   * This call does not record sending metrics as it's asynchronous.
   */
  override fun enqueueAsync(
    queueName: QueueName,
    body: String,
    idempotencyKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ): CompletableFuture<SendMessageResponse> {
    val queueUrl = queueResolver.getQueueUrl(queueName)

    val attrs = attributes.map {
      it.key to MessageAttributeValue.builder().dataType("String").stringValue(it.value).build()
    }.toMap().toMutableMap()
    attrs[SqsJob.JOBQUEUE_METADATA_ATTR] = createMetadataMessageAttributeValue(queueName, idempotencyKey)

    val request = SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody(body)
      .delaySeconds(deliveryDelay?.toSeconds()?.toInt())
      .messageAttributes(attrs)
      .build()

    val timer = sqsMetrics.sqsSendTime.labels(queueName.value).startTimer()
    return try {
      val response = client.sendMessage(request)
      sqsMetrics.jobsEnqueued.labels(queueName.value).inc()
      response.whenComplete { _, _ ->
        timer.observeDuration()
      }
    } catch (e: Exception) {
      sqsMetrics.jobEnqueueFailures.labels(queueName.value).inc()
      throw e
    }
  }

  private fun createMetadataMessageAttributeValue(
    queueName: QueueName,
    idempotencyKey: String,
  ): MessageAttributeValue {
    // TODO old implementation passes the span id as well
    val metadata = mutableMapOf(
      SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
      SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY to idempotencyKey,
    )

    return MessageAttributeValue.builder()
      .dataType("String")
      .stringValue(moshi.adapter<Map<String, String>>().toJson(metadata))
      .build()
  }
}
