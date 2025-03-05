package misk.jobqueue.v2

import misk.jobqueue.QueueName
import java.time.Duration
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
  )

  /**
   * Enqueue the job and block waiting for the confirmation.
   */
  fun enqueueBlocking(
    queueName: QueueName,
    body: String,
    idempotencyKey: String? = null,
    deliveryDelay: Duration? = Duration.ZERO,
    attributes: Map<String, String> = emptyMap(),
  )

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
}
