package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import misk.jobqueue.QueueName
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import wisp.lease.LeaseManager
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal class AwsSqsQueueAttributeImporter @Inject constructor(
  private val config: AwsSqsJobQueueConfig,
  private val leaseManager: LeaseManager,
  private val metrics: SqsMetrics,
  private val queues: QueueResolver,
  @ForSqsHandling private val taskQueue: RepeatedTaskQueue
) {

  val running = AtomicBoolean(true)

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
      if (!running.get()) {
        return@scheduleWithBackoff Status.NO_RESCHEDULE
      }
      val queue = queues.getForSending(queueName)
      val lease = leaseManager.requestLease("sqs-queue-attributes-${queue.queueName}")
      var leaseHeld = lease.checkHeld()
      if (!leaseHeld) {
        leaseHeld = lease.acquire()
      }
      if (!leaseHeld) {
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

  fun shutDown() {
    running.set(false)
  }

  companion object {
    private val log = getLogger<AwsSqsQueueAttributeImporter>()
    internal const val metricNamespace = "AWS/SQS"
    internal const val metricStat = "sum"
  }
}
