package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.BatchResultErrorEntry
import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.SendMessageBatchRequest
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.squareup.moshi.Moshi
import datadog.trace.core.DDSpan
import io.opentracing.Tracer
import misk.jobqueue.Job
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.moshi.adapter
import misk.time.timed
import misk.tracing.traceWithSpan
import wisp.logging.getLogger
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
  override fun enqueue(
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    // Ensure there are at most 8 attributes; AWS SQS enforces a limit of 10 custom attributes
    // per message, 2 of which are reserved for this library (trace id and jobqueue metadata).
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-attributes.html
    // TODO(bruno): change to 9 once we drop trace id
    check(attributes.size <= 8) {
      "a maximum of 8 attributes are supported (got ${attributes.size})"
    }

    tracer.traceWithSpan("enqueue-job-${queueName.value}") { span ->
      metrics.jobsEnqueued.labels(queueName.value, queueName.value).inc()
      try {
        val queue = queues.getForSending(queueName)

        val (sendDuration, _) = queue.call { client ->
          val sendRequest = SendMessageRequest().apply {
            queueUrl = queue.url
            messageBody = body
            if (deliveryDelay != null) delaySeconds = (deliveryDelay.toMillis() / 1000).toInt()
            attributes.forEach { (key, value) ->
              addMessageAttributesEntry(
                key,
                MessageAttributeValue()
                  .withDataType("String")
                  .withStringValue(value)
              )
            }

            val metadata = mutableMapOf(
              SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
              SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY to idempotenceKey
            )

            // Preserve original trace id, if available.
            (span as? DDSpan)?.let {
              val traceId = it.context().traceId.toString()
              metadata[SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID] = traceId
              // TODO(bruno): drop this attribute after rollout; moved to metadata
              addMessageAttributesEntry(
                SqsJob.ORIGINAL_TRACE_ID_ATTR,
                MessageAttributeValue().withDataType("String").withStringValue(traceId)
              )
            }

            // Add the internal metadata dictionary, encoded as JSON.
            addMessageAttributesEntry(
              SqsJob.JOBQUEUE_METADATA_ATTR,
              MessageAttributeValue()
                .withDataType("String")
                .withStringValue(moshi.adapter<Map<String, String>>().toJson(metadata))
            )
          }

          timed {
            client.sendMessage(sendRequest)
          }
        }

        metrics.sqsSendTime.record(
          sendDuration.toMillis().toDouble(),
          queueName.value,
          queueName.value
        )
      } catch (th: Throwable) {
        log.error(th) { "failed to enqueue to ${queueName.value}" }
        metrics.jobEnqueueFailures.labels(queueName.value, queueName.value).inc()
        throw th
      }
    }
  }

  override fun batchEnqueue(
    queueName: QueueName,
    jobs: List<JobQueue.JobRequest>,
  ): JobQueue.BatchEnqueueResult {
    return tracer.traceWithSpan("batch-enqueue-job-${queueName.value}") { span ->
      metrics.jobsEnqueued.labels(queueName.value, queueName.value).inc(jobs.size.toDouble())
      try {
        check(jobs.size <= 10) {
          "a maximum of 10 jobs can be batched."
        }

        val queue = queues.getForSending(queueName)

        val (sendDuration, result) = queue.call { client ->
          val messageEntries = jobs.map { message ->
            // Ensure there are at most 8 attributes; AWS SQS enforces a limit of 10 custom attributes
            // per message, 2 of which are reserved for this library (trace id and jobqueue metadata).
            // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-attributes.html
            // TODO(bruno): change to 9 once we drop trace id
            check(message.attributes.size <= 8) {
              "a maximum of 8 attributes are supported (got ${message.attributes.size} for job with idempotenceKey:${message.idempotenceKey})"
            }

            SendMessageBatchRequestEntry(
              message.idempotenceKey,
              message.body,
            ).apply {
              if (message.deliveryDelay != null) delaySeconds =
                (message.deliveryDelay?.toMillis() ?: 0 / 1000).toInt()
              message.attributes.forEach { (key, value) ->
                addMessageAttributesEntry(
                  key,
                  MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(value)
                )
              }

              val metadata = mutableMapOf(
                SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE to queueName.parentQueue.value,
                SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY to message.idempotenceKey
              )

              // Preserve original trace id, if available.
              (span as? DDSpan)?.let {
                val traceId = it.context().traceId.toString()
                metadata[SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID] = traceId
                // TODO(bruno): drop this attribute after rollout; moved to metadata
                addMessageAttributesEntry(
                  SqsJob.ORIGINAL_TRACE_ID_ATTR,
                  MessageAttributeValue().withDataType("String").withStringValue(traceId)
                )
              }

              addMessageAttributesEntry(
                SqsJob.JOBQUEUE_METADATA_ATTR,
                MessageAttributeValue()
                  .withDataType("String")
                  .withStringValue(moshi.adapter<Map<String, String>>().toJson(metadata))
              )
            }
          }

          timed {
            client.sendMessageBatch(SendMessageBatchRequest(queue.url, messageEntries))
          }
        }

        metrics.sqsSendTime.record(
          sendDuration.toMillis().toDouble(),
          queueName.value,
          queueName.value
        )

        val batchResult = JobQueue.BatchEnqueueResult(result.successful.map { it.id }, result.failed.map {
          JobQueue.EnqueueErrorResult(
            it.id,
            it.isSenderFault,
            it.code,
            it.message
          )
        })

        if(batchResult.failed.isNotEmpty()){
          log.error { "failed to batch enqueue to ${queueName.value}" }
          metrics.jobEnqueueFailures.labels(queueName.value, queueName.value).inc(batchResult.failed.size.toDouble())
        }
        batchResult
      } catch (th: Throwable) {
        log.error(th) { "failed to enqueue to ${queueName.value}" }
        metrics.jobEnqueueFailures.labels(queueName.value, queueName.value).inc()
        throw th
      }
    }
  }

  companion object {
    private val log = getLogger<SqsJobQueue>()
  }
}
