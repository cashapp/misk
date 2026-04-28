package misk.aws2.sqs.jobqueue.leased

import jakarta.inject.Inject
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import wisp.lease.LeaseManager

internal class AwsSqsQueueAttributeImporter
@Inject
constructor(
  private val config: AwsSqsJobQueueConfig,
  private val leaseManager: LeaseManager,
  private val metrics: SqsMetrics,
  private val queues: QueueResolver,
  @ForSqsHandling private val taskQueue: RepeatedTaskQueue,
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
      val leaseHeld = lease.checkHeld() || lease.acquire()
      if (!leaseHeld) {
        metrics.sqsApproxNumberOfMessages.clear()
        metrics.sqsApproxNumberOfMessagesNotVisible.clear()
      } else {
        log.info { "recording metrics for queue ${queueName.value}" }
        val attributes =
          queue.call { client ->
            client
              .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                  .queueUrl(queue.url)
                  .attributeNames(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                  )
                  .build()
              )
              .attributes()
          }

        attributes[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.let {
          metrics.sqsApproxNumberOfMessages
            .labels(metricNamespace, metricStat, queue.queueName, queue.queueName)
            .set(it.toDouble())
        }
        attributes[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]?.let {
          metrics.sqsApproxNumberOfMessagesNotVisible
            .labels(metricNamespace, metricStat, queue.queueName, queue.queueName)
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
