package misk.aws2.sqs.jobqueue.coordinated

import com.squareup.moshi.Moshi
import io.opentracing.Span
import io.opentracing.Tracer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import misk.aws2.sqs.jobqueue.parentQueue
import misk.jobqueue.JobQueue
import misk.jobqueue.JobQueue.Companion.SQS_MAX_BATCH_ENQUEUE_JOB_SIZE
import misk.jobqueue.QueueName
import misk.moshi.adapter
import misk.time.timed
import misk.tracing.traceWithSpan
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Singleton
internal class SqsJobQueue
@Inject
internal constructor(
  private val queues: QueueResolver,
  private val metrics: SqsMetrics,
  private val moshi: Moshi,
  private val tracer: Tracer,
) : JobQueue {

  private fun checkAttributeSize(attributes: Map<String, String>) {
    check(attributes.size <= 9) { "a maximum of 9 attributes are supported (got ${attributes.size})" }
  }

  override fun enqueue(
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ) {
    executeWithTracingAndErrorHandling(queueName, 1) { span: Span, queue: ResolvedQueue ->
      val request =
        SendMessageRequest.builder()
          .queueUrl(queue.url)
          .messageBody(body)
          .apply { if (deliveryDelay != null) delaySeconds(getDelaySeconds(deliveryDelay)) }
          .messageAttributes(
            attributes
              .mapValues { it.value.toMessageAttributeValue() }
              .plus(SqsJob.JOBQUEUE_METADATA_ATTR to createMetadataMessageAttributeValue(span, queueName, idempotenceKey))
          )
          .build()
      val (sendDuration) =
        timed {
          queue.callSend(
            unbufferedLambda = { it.sendMessage(request) },
            bufferedLambda = { it.sendMessage(request) },
          )
        }
      return@executeWithTracingAndErrorHandling sendDuration
    }
  }

  override fun batchEnqueue(queueName: QueueName, jobs: List<JobQueue.JobRequest>) {
    check(jobs.size <= SQS_MAX_BATCH_ENQUEUE_JOB_SIZE) { "a maximum of 10 jobs can be batched." }

    executeWithTracingAndErrorHandling(queueName, jobs.size) { span: Span, queue: ResolvedQueue ->
      val (sendDuration, batchResult) =
        queue.call { client ->
          val messageEntries =
            jobs.map { message ->
              checkAttributeSize(message.attributes)

              SendMessageBatchRequestEntry.builder()
                .id(message.idempotenceKey)
                .messageBody(message.body)
                .apply { if (message.deliveryDelay != null) delaySeconds(getDelaySeconds(message.deliveryDelay)) }
                .messageAttributes(
                  message.attributes
                    .mapValues { it.value.toMessageAttributeValue() }
                    .plus(
                      SqsJob.JOBQUEUE_METADATA_ATTR to
                        createMetadataMessageAttributeValue(span, queueName, message.idempotenceKey)
                    )
                )
                .build()
            }

          timed {
            client.sendMessageBatch(SendMessageBatchRequest.builder().queueUrl(queue.url).entries(messageEntries).build())
          }
        }

      if (batchResult.failed().isNotEmpty()) {
        throw JobQueue.BatchEnqueueException(
          queueName,
          batchResult.successful().map { it.id() },
          batchResult.failed().map { JobQueue.EnqueueErrorResult(it.id(), it.senderFault(), it.code(), it.message()) },
        )
      }

      return@executeWithTracingAndErrorHandling sendDuration
    }
  }

  private fun String.toMessageAttributeValue(): MessageAttributeValue =
    MessageAttributeValue.builder().dataType("String").stringValue(this).build()

  private fun getDelaySeconds(deliveryDelay: Duration?): Int {
    val delayMillis = deliveryDelay?.toMillis() ?: 0
    return (delayMillis / 1000).toInt()
  }

  private fun createMetadataMessageAttributeValue(
    span: Span,
    queueName: QueueName,
    idempotenceKey: String,
  ): MessageAttributeValue {
    val metadata =
      mutableMapOf(
        SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
        SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY to idempotenceKey,
      )

    span
      .context()
      .toTraceId()
      ?.takeIf { it.isNotBlank() }
      ?.let { metadata[SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID] = it }

    return MessageAttributeValue.builder()
      .dataType("String")
      .stringValue(moshi.adapter<Map<String, String>>().toJson(metadata))
      .build()
  }

  private fun executeWithTracingAndErrorHandling(
    queueName: QueueName,
    jobCount: Int,
    f: (span: Span, queue: ResolvedQueue) -> Duration,
  ) {
    tracer.traceWithSpan("enqueue-job-${queueName.value}") { span ->
      metrics.jobsEnqueued.labels(queueName.value, queueName.value).inc(jobCount.toDouble())
      try {
        val queue = queues.getForSending(queueName)
        val sendDuration = f(span, queue)

        metrics.sqsSendTime.record(sendDuration.toMillis().toDouble(), queueName.value, queueName.value)
      } catch (batchEnqueueException: JobQueue.BatchEnqueueException) {
        metrics.jobEnqueueFailures
          .labels(queueName.value, queueName.value)
          .inc(batchEnqueueException.failed.size.toDouble())
        throw batchEnqueueException
      } catch (th: Throwable) {
        metrics.jobEnqueueFailures.labels(queueName.value, queueName.value).inc()
        throw th
      }
    }
  }
}
