package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.common.annotations.VisibleForTesting
import com.squareup.moshi.Moshi
import misk.jobqueue.Job
import misk.jobqueue.QueueName
import misk.moshi.adapter
import misk.time.timed
import java.math.BigInteger
import kotlin.random.Random

internal class SqsJob(
  override val queueName: QueueName,
  private val queues: QueueResolver,
  private val metrics: SqsMetrics,
  private val moshi: Moshi,
  private val message: Message
) : Job {
  override val body: String = message.body
  override val id: String = message.messageId
  override val idempotenceKey: String by lazy {
    jobqueueMetadata[JOBQUEUE_METADATA_IDEMPOTENCE_KEY]
      ?: error("$JOBQUEUE_METADATA_IDEMPOTENCE_KEY not set in $JOBQUEUE_METADATA_ATTR")
  }
  override val attributes: Map<String, String> by lazy {
    message.messageAttributes
      .filter { (key, _) -> key != JOBQUEUE_METADATA_ATTR }
      .map { (key, value) -> key to value.stringValue }.toMap()
      .plus(message.attributes)
  }

  private val queue: ResolvedQueue = queues.getForReceiving(queueName)
  private val jobqueueMetadata: Map<String, String> by lazy {
    val metadata = message.messageAttributes[JOBQUEUE_METADATA_ATTR]
      ?: throw IllegalStateException(JOBQUEUE_METADATA_ATTR + " not found in messageAttributes")
    moshi.adapter<Map<String, String>>().fromJson(metadata.stringValue)!!
  }

  override fun acknowledge() {
    deleteMessage(queue, message)
    metrics.jobsAcknowledged.labels(queueName.value, queueName.value).inc()
  }

  override fun deadLetter() {
    val dlq = queues.getDeadLetter(queueName)
    dlq.call { client ->
      client.sendMessage(
        SendMessageRequest()
          .withQueueUrl(dlq.url)
          .withMessageBody(body)
          // Preserves original jobqueue metadata and trace id.
          .withMessageAttributes(message.messageAttributes)
      )
    }
    deleteMessage(queue, message)
    metrics.jobsDeadLettered.labels(queueName.value, queueName.value).inc()
  }

  /**
   *  Assign a visibility timeout for some duration X making the job invisible to the consumers for
   *  that duration. With every subsequent retry the duration becomes longer until it hits the max
   *  value of 10hrs.
   */
  override fun delayWithBackoff() {
    val maxReceiveCount = queue.maxRetries
    val visibilityTime = calculateVisibilityTimeOut(
      currentReceiveCount = attributes[RECEIVE_COUNT]?.toInt()  ?: 1,
      maxReceiveCount = maxReceiveCount,
    )

    queue.call { client ->
      client.changeMessageVisibility(
        ChangeMessageVisibilityRequest()
          .withQueueUrl(queue.url)
          .withReceiptHandle(message.receiptHandle)
          .withVisibilityTimeout(visibilityTime)
      )
    }
    metrics.visibilityTime.labels(queueName.value, queueName.value).set(visibilityTime.toDouble())
  }
  private fun deleteMessage(queue: ResolvedQueue, message: Message) {
    val (deleteDuration) = queue.call {
      timed { it.deleteMessage(queue.url, message.receiptHandle) }
    }
    metrics.sqsDeleteTime.record(
      deleteDuration.toMillis().toDouble(),
      queueName.value,
      queueName.value
    )
  }

  companion object {
    /** We are limited with 12hrs after which SQS would thrown an exception - to be safe we set it to 10hrs. */
    const val MAX_JOB_DELAY = 10 * 60 * 60L
    const val RECEIVE_COUNT = "ApproximateReceiveCount"
    /** Message attribute that captures original trace id for a job, when available. */
    const val ORIGINAL_TRACE_ID_ATTR = "x-original-trace-id"

    /** Message attribute used to store metadata specific to jobqueue functionality.
     * JSON-encoded.
     * */
    const val JOBQUEUE_METADATA_ATTR = "_jobqueue-metadata"

    /**
     * The name of the queue the job was originally submitted to.
     * Used when operating with a global dead-letter queue,
     * so that jobs can be returned to their original (or retry) queue when reprocessing.
     */
    const val JOBQUEUE_METADATA_ORIGIN_QUEUE = "origin_queue"

    /** Client-assigned identifier, useful to detect duplicate messages. */
    const val JOBQUEUE_METADATA_IDEMPOTENCE_KEY = "idempotence_key"
    const val JOBQUEUE_METADATA_ORIGINAL_TRACE_ID = "original_trace_id"

    /** Estimates the current visibility timeout*/
    @VisibleForTesting
    fun calculateVisibilityTimeOut(currentReceiveCount: Int, maxReceiveCount: Int): Int {
      val consecutiveRetryCount = (currentReceiveCount + 1).coerceAtMost(maxReceiveCount)
      val backoff = BigInteger.TWO.pow(consecutiveRetryCount - 1).toLong()
      val backoffWithJitter = MAX_JOB_DELAY.coerceAtMost((backoff / 2 + Random.nextLong(0, backoff / 2)))

      return backoffWithJitter.toInt()
    }
  }
}
