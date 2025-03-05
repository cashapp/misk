package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import ddtrot.dd.trace.core.DDSpan
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import kotlinx.coroutines.future.await
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.parentQueue
import misk.jobqueue.v2.JobEnqueuer
import misk.moshi.adapter
import misk.tokens.TokenGenerator
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Singleton
class SqsJobEnqueuer @Inject constructor(
  private val client: SqsAsyncClient,
  private val queueResolver: QueueResolver,
  private val tokenGenerator: TokenGenerator,
  private val sqsMetrics: SqsMetrics,
  private val moshi: Moshi,
  private val tracer: Tracer,
) : JobEnqueuer {
  /**
   * Enqueue the job and suspend waiting for the confirmation
   */
  override suspend fun enqueue(
    queueName: QueueName,
    body: String,
    idempotencyKey: String?,
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
    idempotencyKey: String?,
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
    idempotencyKey: String?,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ): CompletableFuture<Boolean> {
    return tracer.withSpanAsync("enqueue-job-${queueName.value}") { span, scope ->
      val queueUrl = queueResolver.getQueueUrl(queueName)
      val resolvedIdempotencyKey = idempotencyKey ?: tokenGenerator.generate()

      val attrs = attributes.map {
        it.key to MessageAttributeValue.builder().dataType("String").stringValue(it.value).build()
      }.toMap().toMutableMap()
      attrs[SqsJob.JOBQUEUE_METADATA_ATTR] =
        createMetadataMessageAttributeValue(queueName, resolvedIdempotencyKey, span)

      val request = SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(body)
        .delaySeconds(deliveryDelay?.toSeconds()?.toInt())
        .messageAttributes(attrs)
        .build()

      val timer = sqsMetrics.sqsSendTime.labels(queueName.value).startTimer()
      try {
        val response = client.sendMessage(request)
        sqsMetrics.jobsEnqueued.labels(queueName.value).inc()
        response.whenComplete { _, _ ->
          timer.observeDuration()
          span.finish()
          scope.close()
        }.thenCompose { CompletableFuture.supplyAsync { true } }
      } catch (e: Exception) {
        sqsMetrics.jobEnqueueFailures.labels(queueName.value).inc()
        throw e
      }
    }
  }

  private fun <T> Tracer.withSpanAsync(spanName: String, block: (span: Span, scope: Scope) -> T): T {
    val span = tracer.buildSpan(spanName).start()
    val scope = scopeManager().activate(span)
    try {
      return block(span, scope)
    } catch (t: Throwable) {
      Tags.ERROR.set(span, true)
      throw t
    }
  }

  private fun createMetadataMessageAttributeValue(
    queueName: QueueName,
    idempotencyKey: String,
    span: Span,
  ): MessageAttributeValue {
    val metadata = mutableMapOf(
      SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
      SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY to idempotencyKey,
    )

    // Preserve original trace id, if available.
    (span as? DDSpan)?.let {
      val traceId = it.context().traceId.toString()
      metadata[SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID] = traceId
    }

    return MessageAttributeValue.builder()
      .dataType("String")
      .stringValue(moshi.adapter<Map<String, String>>().toJson(metadata))
      .build()
  }
}
