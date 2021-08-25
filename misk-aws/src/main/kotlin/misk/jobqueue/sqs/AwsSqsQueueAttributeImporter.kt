package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import misk.jobqueue.QueueName
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import wisp.lease.LeaseManager
import wisp.logging.getLogger
import java.time.Duration
import javax.inject.Inject

internal class AwsSqsQueueAttributeImporter @Inject constructor(
  private val config: AwsSqsJobQueueConfig,
  private val leaseManager: LeaseManager,
  private val metrics: SqsMetrics,
  private val queues: QueueResolver,
  @ForSqsHandling private val taskQueue: RepeatedTaskQueue
) {

  /**
   * Spawn a new thread that will consume the [queueName] SQS attributes and record them as metrics
   *
   * The metric recorded are:
   * - ApproximateNumberOfMessagesVisible
   * - ApproximateNumberOfMessagesNotVisible
   */
  fun import(queueName: QueueName) {
    val frequency = Duration.ofMillis(config.queue_attribute_importer_frequency_ms)

    taskQueue.scheduleWithBackoff(frequency) {
      val queue = queues.getForSending(queueName)
      val lease = leaseManager.requestLease("sqs-queue-attributes-${queue.queueName}")
      if (!lease.checkHeld()) {
        metrics.sqsApproxNumberOfMessages.clear()
        metrics.sqsApproxNumberOfMessagesNotVisible.clear()
      } else {
        log.info {
          "recording metrics for queue ${queueName.value}"
        }
        val attributes = queue.call { client ->
          val request = GetQueueAttributesRequest()
            .withQueueUrl(queue.url)
            .withAttributeNames(
              QueueAttributeName.ApproximateNumberOfMessages,
              QueueAttributeName.ApproximateNumberOfMessagesNotVisible
            )
          val response = client.getQueueAttributes(request)
          response.attributes
        }

        attributes[QueueAttributeName.ApproximateNumberOfMessages.toString()]?.let {
          metrics.sqsApproxNumberOfMessages.labels(
            metricNamespace,
            metricStat,
            queue.queueName,
            queue.queueName
          )
            .set(it.toDouble())
        }
        attributes[QueueAttributeName.ApproximateNumberOfMessagesNotVisible.toString()]?.let {
          metrics.sqsApproxNumberOfMessagesNotVisible.labels(
            metricNamespace,
            metricStat,
            queue.queueName,
            queue.queueName
          )
            .set(it.toDouble())
        }
      }
      Status.OK
    }
  }

  companion object {
    private val log = getLogger<AwsSqsQueueAttributeImporter>()
    internal const val metricNamespace = "AWS/SQS"
    internal const val metricStat = "sum"
  }
}
