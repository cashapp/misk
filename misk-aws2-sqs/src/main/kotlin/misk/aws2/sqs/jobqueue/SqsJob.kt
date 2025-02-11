package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import misk.jobqueue.QueueName
import misk.jobqueue.v2.Job
import misk.moshi.adapter
import software.amazon.awssdk.services.sqs.model.Message

class SqsJob(
  override val queueName: QueueName,
  private val moshi: Moshi,
  val message: Message,
  val queueUrl: String,
  val publishToChannelTimestamp: Long,
) : Job {
  override val body: String = message.body()
  override val attributes: Map<String, String>
    get() = emptyMap()
  override val id: String = message.messageId()
  override val idempotenceKey: String by lazy {
    jobqueueMetadata[JOBQUEUE_METADATA_IDEMPOTENCE_KEY]
      ?: error("$JOBQUEUE_METADATA_IDEMPOTENCE_KEY not set in $JOBQUEUE_METADATA_ATTR")
  }

  private val jobqueueMetadata: Map<String, String> by lazy {
    val metadata = message.messageAttributes()[JOBQUEUE_METADATA_ATTR]
      ?: throw IllegalStateException(JOBQUEUE_METADATA_ATTR + " not found in messageAttributes")
    moshi.adapter<Map<String, String>>().fromJson(metadata.stringValue())!!
  }

  companion object {
    /** Message attribute used to store metadata specific to jobqueue functionality.
     * JSON-encoded.
     */
    const val JOBQUEUE_METADATA_ATTR = "_jobqueue-metadata"

    /**
     * The name of the queue the job was originally submitted to.
     * Used when operating with a global dead-letter queue,
     * so that jobs can be returned to their original (or retry) queue when reprocessing.
     */
    const val JOBQUEUE_METADATA_ORIGIN_QUEUE = "origin_queue"

    /**
     * Client-assigned identifier, useful to detect duplicate messages.
     */
    const val JOBQUEUE_METADATA_IDEMPOTENCE_KEY = "idempotence_key"
  }
}
