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
    val successfulIds: List<String>,        // Successfully enqueued message IDs (your batch entry IDs)
    val invalidIds: List<String>,           // Invalid message IDs - don't retry (client errors)
    val retriableIds: List<String>          // Retriable message IDs - can retry (server errors)
  )

  /**
   * Data class containing the necessary information to be enqueued in a batch enqueue
   * @param body The body of the job;
   * @param idempotencyKey Client-assigned unique key. Defaults to a randomly generated UUID when not explicitly set.
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration.
   * Note that depending on implementation, there may be an upper limit to this value. For instance, SQS implementation
   * limits `deliveryDelay` to 900s (15m).
   * @param attributes Arbitrary contextual attributes associated with the job.
   */
  data class JobRequest @JvmOverloads constructor(
    val body: String,
    val idempotencyKey : String? = null,
    val deliveryDelay: Duration? = null,
    val attributes: Map<String, String> = emptyMap()
  )


  companion object {
    const val SQS_MAX_BATCH_ENQUEUE_JOB_SIZE = 10
  }
}
