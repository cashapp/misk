package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.SendMessageBatchRequest
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.squareup.moshi.Moshi
import ddtrot.dd.trace.core.DDSpan
import io.opentracing.Span
import io.opentracing.Tracer
import misk.jobqueue.JobQueue
import misk.jobqueue.JobQueue.Companion.SQS_MAX_BATCH_ENQUEUE_JOB_SIZE
import misk.jobqueue.QueueName
import misk.moshi.adapter
import misk.time.timed
import wisp.tracing.traceWithSpan
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SqsJobQueue @Inject internal constructor(
  private val queues: QueueResolver,
  private val metrics: SqsMetrics,
  private val moshi: Moshi,
  private val tracer: Tracer
) : JobQueue {

  private fun checkAttributeSize(attributes: Map<String, String>) {
    // Ensure there are at most 9 attributes; AWS SQS enforces a limit of 10 custom attributes
    // per message, 1 of which is reserved for this library (jobqueue metadata).
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-attributes.html
    check(attributes.size <= 9) {
      "a maximum of 9 attributes are supported (got ${attributes.size})"
    }
  }

  override fun enqueue(
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    executeWithTracingAndErrorHandling(queueName, 1) { span: Span, queue: ResolvedQueue ->
      val (sendDuration, _) = queue.call { client ->
        val sendRequest = SendMessageRequest().apply {
          queueUrl = queue.url
          messageBody = body
          if (deliveryDelay != null) delaySeconds = getDelaySeconds(deliveryDelay)
          attributes.forEach { (key, value) ->
            addMessageAttributesEntry(
              key,
              value.toMessageAttributeValue()
            )
          }

          // Add the internal metadata dictionary, encoded as JSON.
          addMessageAttributesEntry(
            SqsJob.JOBQUEUE_METADATA_ATTR,
            createMetadataMessageAttributeValue(span, queueName, idempotenceKey)
          )
        }

        timed {
          client.sendMessage(sendRequest)
        }
      }
      return@executeWithTracingAndErrorHandling sendDuration
    }
  }

  override fun batchEnqueue(
    queueName: QueueName,
    jobs: List<JobQueue.JobRequest>,
  ) {
    check(jobs.size <= SQS_MAX_BATCH_ENQUEUE_JOB_SIZE) {
      "a maximum of 10 jobs can be batched."
    }

    executeWithTracingAndErrorHandling(queueName, jobs.size) { span: Span, queue: ResolvedQueue ->
      val (sendDuration, batchResult) = queue.call { client ->
          val messageEntries = jobs.map { message ->
            checkAttributeSize(message.attributes)

            SendMessageBatchRequestEntry(
              message.idempotenceKey,
              message.body,
            ).apply {
              if (message.deliveryDelay != null) delaySeconds =
                getDelaySeconds(message.deliveryDelay)
              message.attributes.forEach { (key, value) ->
                addMessageAttributesEntry(
                  key,
                  value.toMessageAttributeValue()
                )
              }

              addMessageAttributesEntry(
                SqsJob.JOBQUEUE_METADATA_ATTR,
                createMetadataMessageAttributeValue(span, queueName, message.idempotenceKey)
              )
            }
          }

          timed {
            client.sendMessageBatch(SendMessageBatchRequest(queue.url, messageEntries))
          }
        }

      if(batchResult.failed.size > 0) {
        throw JobQueue.BatchEnqueueException(queueName, batchResult.successful.map { it.id }, batchResult.failed.map {
          JobQueue.EnqueueErrorResult(
            it.id,
            it.isSenderFault,
            it.code,
            it.message
          )
        })
      }

      return@executeWithTracingAndErrorHandling sendDuration
    }
  }

  private fun String.toMessageAttributeValue(): MessageAttributeValue = MessageAttributeValue()
    .withDataType("String")
    .withStringValue(this)

  private fun getDelaySeconds(deliveryDelay: Duration?): Int {
    val delayMillis = deliveryDelay?.toMillis() ?: 0
    return (delayMillis / 1000).toInt()
  }

  private fun createMetadataMessageAttributeValue(span: Span,
    queueName: QueueName,
    idempotenceKey: String): MessageAttributeValue {
    val metadata = mutableMapOf(
      SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
      SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY to idempotenceKey
    )

    // Preserve original trace id, if available.
    (span as? DDSpan)?.let {
      val traceId = it.context().traceId.toString()
      metadata[SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID] = traceId
    }

    return MessageAttributeValue()
      .withDataType("String")
      .withStringValue(moshi.adapter<Map<String, String>>().toJson(metadata))
  }

  private fun executeWithTracingAndErrorHandling(queueName: QueueName, jobCount: Int, f: (span: Span, queue: ResolvedQueue) -> Duration){
    tracer.traceWithSpan("enqueue-job-${queueName.value}") { span ->
      metrics.jobsEnqueued.labels(queueName.value, queueName.value).inc(jobCount.toDouble())
      try {
        val queue = queues.getForSending(queueName)
        val sendDuration = f(span, queue)

        metrics.sqsSendTime.record(
          sendDuration.toMillis().toDouble(),
          queueName.value,
          queueName.value
        )
      } catch (batchEnqueueException: JobQueue.BatchEnqueueException) {
        metrics.jobEnqueueFailures.labels(queueName.value, queueName.value)
          .inc(batchEnqueueException.failed.size.toDouble())
        throw batchEnqueueException
      } catch (th: Throwable) {
        metrics.jobEnqueueFailures.labels(queueName.value, queueName.value).inc()
        throw th
      }
    }
  }
}
