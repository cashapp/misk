package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.SendMessageRequest
import io.opentracing.Tracer
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.tracing.traceWithSpan
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SqsJobQueue @Inject internal constructor(
  private val queues: QueueResolver,
  private val metrics: SqsMetrics,
  private val tracer: Tracer
) : JobQueue {
  override fun enqueue(
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    tracer.traceWithSpan("enqueue-job-${queueName.value}") { span ->
      metrics.jobsEnqueued.labels(queueName.value).inc()
      try {
        val queue = queues[queueName]

        queue.client.sendMessage(SendMessageRequest().apply {
          queueUrl = queue.url
          messageBody = body
          if (deliveryDelay != null) delaySeconds = (deliveryDelay.toMillis() / 1000).toInt()
          attributes.forEach { (key, value) ->
            addMessageAttributesEntry(key, MessageAttributeValue()
                .withDataType("String")
                .withStringValue(value))
          }

          // Save the original trace id, if we can determine it
          // TODO(mmihic): Should put this case somewhere in the tracing modules
          (span as? com.uber.jaeger.Span)?.let {
            addMessageAttributesEntry(
                SqsJob.ORIGINAL_TRACE_ID_ATTR,
                MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(it.context().traceId.toString()))
          }
        })
      } catch (th: Throwable) {
        log.error(th) { "failed to enqueue to ${queueName.value}" }
        metrics.jobEnqueueFailures.labels(queueName.value).inc()
        throw th
      }
    }
  }

  companion object {
    private val log = getLogger<SqsJobQueue>()
  }
}