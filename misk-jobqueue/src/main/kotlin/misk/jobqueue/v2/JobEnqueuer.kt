package misk.jobqueue.v2

import kotlinx.coroutines.future.await
import misk.jobqueue.QueueName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface JobEnqueuer {
  /**
   * Enqueue the job and suspend waiting for the confirmation
   */
  suspend fun enqueue(
    queueName: QueueName,
    body: String,
    idempotencyKey: String? = null,
    deliveryDelay: Duration? = Duration.ZERO,
    attributes: Map<String, String> = emptyMap(),
  ) {
    enqueueAsync(queueName, body, idempotencyKey, deliveryDelay, attributes).await()
  }

  /**
   * Enqueue the job and block waiting for the confirmation.
   */
  fun enqueueBlocking(
    queueName: QueueName,
    body: String,
    idempotencyKey: String? = null,
    deliveryDelay: Duration? = Duration.ZERO,
    attributes: Map<String, String> = emptyMap(),
  ) {
    enqueueAsync(queueName, body, idempotencyKey, deliveryDelay, attributes).join()
  }

  /**
   * Enqueue the job and return a CompletableFuture.
   */
  fun enqueueAsync(
    queueName: QueueName,
    body: String,
    idempotencyKey: String? = null,
    deliveryDelay: Duration? = Duration.ZERO,
    attributes: Map<String, String> = emptyMap(),
  ): CompletableFuture<Boolean>

  /**
   * Batch enqueue jobs and suspend waiting for the result.
   * Maximum batch size is 10 messages.
   *
   * @return BatchEnqueueResult with detailed success/failure breakdown
   */
  suspend fun batchEnqueue(
    queueName: QueueName,
    jobs: List<JobRequest>,
  ): BatchEnqueueResult {
    return batchEnqueueAsync(queueName, jobs).await()
  }

  /**
   * Batch enqueue jobs and block waiting for the result.
   * Maximum batch size is 10 messages.
   *
   * @return BatchEnqueueResult with detailed success/failure breakdown
   */
  fun batchEnqueueBlocking(
    queueName: QueueName,
    jobs: List<JobRequest>,
  ): BatchEnqueueResult {
    return batchEnqueueAsync(queueName, jobs).join()
  }

  /**
   * Batch enqueue jobs and return a CompletableFuture.
   * Maximum batch size is 10 messages.
   *
   * @return CompletableFuture<BatchEnqueueResult> with detailed success/failure breakdown
   */
  fun batchEnqueueAsync(
    queueName: QueueName,
    jobs: List<JobRequest>,
  ): CompletableFuture<BatchEnqueueResult>

  /**
   * Result of a batch enqueue operation with message ID focus
   */
  data class BatchEnqueueResult(
    val isFullySuccessful: Boolean,
    val successful: List<String>,           // Successfully enqueued message IDs (your batch entry IDs)
    val invalid: List<String>,              // Invalid message IDs - don't retry (client errors)
    val retriable: List<String>             // Retriable message IDs - can retry (server errors)
  ) {
    // Convenience properties
    val hasPartialFailure: Boolean = successful.isNotEmpty() && (invalid.isNotEmpty() || retriable.isNotEmpty())
    val hasAnyFailure: Boolean = invalid.isNotEmpty() || retriable.isNotEmpty()
    val totalProcessed: Int = successful.size + invalid.size + retriable.size
    val successCount: Int = successful.size
    val failureCount: Int = invalid.size + retriable.size
  }

  /**
   * Data class containing the necessary information to be enqueued in a batch enqueue
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body.
   * @param idempotencyKey Client-assigned unique key, useful for application code to detect duplicate work.
   * Implementations of both [JobEnqueuer] and [JobConsumer] are expected to _not_ perform any filtering based on this
   * value, as it carries meaning only for application code (i.e. any logic around this property should take place in
   * [JobHandler]s). Defaults to a randomly generated UUID when not explicitly set.
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * Note that depending on implementation, there may be an upper limit to this value. For instance, SQS implementation
   * limits `deliveryDelay` to 900s (15m).
   * @param attributes Arbitrary contextual attributes associated with the job. Implementations may limit the number of
   * attributes per message.
   */
  data class JobRequest @JvmOverloads constructor(
    val body: String,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val deliveryDelay: Duration? = null,
    val attributes: Map<String, String> = emptyMap()
  )


  companion object {
    const val SQS_MAX_BATCH_ENQUEUE_JOB_SIZE = 10
  }
}
