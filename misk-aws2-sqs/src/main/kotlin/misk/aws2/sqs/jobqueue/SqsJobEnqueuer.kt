package misk.aws2.sqs.jobqueue

import com.google.inject.Inject
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import ddtrot.dd.trace.core.DDSpan
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.parentQueue
import misk.jobqueue.v2.JobEnqueuer
import misk.moshi.adapter
import misk.tokens.TokenGenerator
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Singleton
class SqsJobEnqueuer @Inject constructor(
  private val sqsClientFactory: SqsClientFactory,
  private val sqsConfig: SqsConfig,
  private val sqsQueueResolver: SqsQueueResolver,
  private val tokenGenerator: TokenGenerator,
  private val sqsMetrics: SqsMetrics,
  private val moshi: Moshi,
  private val tracer: Tracer,
  private val clock: Clock,
) : JobEnqueuer {
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
      val queueUrl = sqsQueueResolver.getQueueUrl(queueName)
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

      val startTime = clock.millis()
      try {
        val region = sqsConfig.getQueueConfig(queueName).region!!
        val client = sqsClientFactory.get(region)
        val response = client.sendMessage(request)
        sqsMetrics.jobsEnqueued.labels(queueName.value).inc()
        response.whenComplete { _, _ ->
          sqsMetrics.sqsSendTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())
          span.finish()
          scope.close()
        }.thenApply { true }
      } catch (e: Exception) {
        sqsMetrics.jobEnqueueFailures.labels(queueName.value).inc()
        throw e
      }
    }
  }

  /**
   * Batch enqueue jobs and return a CompletableFuture.
   * Maximum batch size is 10 messages.
   *
   * @return CompletableFuture<BatchEnqueueResult> with detailed success/failure breakdown
   */
  override fun batchEnqueueAsync(
    queueName: QueueName,
    jobs: List<JobEnqueuer.JobRequest>,
  ): CompletableFuture<JobEnqueuer.BatchEnqueueResult> {
    require(jobs.size <= JobEnqueuer.SQS_MAX_BATCH_ENQUEUE_JOB_SIZE) {
      "a maximum of 10 jobs can be batched (got ${jobs.size})"
    }

    return tracer.withSpanAsync("batch-enqueue-job-${queueName.value}") { span, scope ->
      val queueUrl = sqsQueueResolver.getQueueUrl(queueName)

      // Check attribute size for each job (max 9 attributes per message + 1 reserved for metadata)
      jobs.forEach { job ->
        require(job.attributes.size <= 9) {
          "a maximum of 9 attributes are supported per job (got ${job.attributes.size})"
        }
      }

      val messageEntries = jobs.map { job ->
        val resolvedIdempotencyKey = job.idempotencyKey

        val attrs = job.attributes.map {
          it.key to MessageAttributeValue.builder().dataType("String").stringValue(it.value).build()
        }.toMap().toMutableMap()
        attrs[SqsJob.JOBQUEUE_METADATA_ATTR] =
          createMetadataMessageAttributeValue(queueName, resolvedIdempotencyKey, span)

        SendMessageBatchRequestEntry.builder()
          .id(resolvedIdempotencyKey)
          .messageBody(job.body)
          .apply {
            job.deliveryDelay?.let { delay ->
              delaySeconds(delay.toSeconds().toInt())
            }
          }
          .messageAttributes(attrs)
          .build()
      }

      val request = SendMessageBatchRequest.builder()
        .queueUrl(queueUrl)
        .entries(messageEntries)
        .build()

      val startTime = clock.millis()
      try {
        val region = sqsConfig.getQueueConfig(queueName).region!!
        val client = sqsClientFactory.get(region)
        val response = client.sendMessageBatch(request)

        response.whenComplete { _, _ ->
          sqsMetrics.sqsSendTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())
          span.finish()
          scope.close()
        }.thenApply { result ->
          processBatchResponse(result, queueName)
        }
      } catch (e: Exception) {
        sqsMetrics.jobEnqueueFailures.labels(queueName.value).inc(jobs.size.toDouble())
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

  private fun processBatchResponse(
    response: SendMessageBatchResponse,
    queueName: QueueName
  ): JobEnqueuer.BatchEnqueueResult {
    val successful = response.successful().map { it.id() }
    val invalid = mutableListOf<String>()
    val retriable = mutableListOf<String>()

    // Categorize failures by error type
    response.failed().forEach { failure ->
      if (failure.senderFault()) {
        invalid.add(failure.id())      // Client error - don't retry
      } else {
        retriable.add(failure.id())    // Server error - can retry
      }
    }

    val result = JobEnqueuer.BatchEnqueueResult(
      isFullySuccessful = invalid.isEmpty() && retriable.isEmpty(),
      successful = successful,
      invalid = invalid,
      retriable = retriable
    )

    // Update metrics
    sqsMetrics.jobsEnqueued.labels(queueName.value).inc(result.successCount.toDouble())
    if (result.hasAnyFailure) {
      sqsMetrics.jobEnqueueFailures.labels(queueName.value).inc(result.failureCount.toDouble())
    }

    return result
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
