package misk.jobqueue.v2

import misk.jobqueue.QueueName
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface JobEnqueuer<T> {
  /**
   * Enqueue the job and suspend waiting for the confirmation
   */
  suspend fun enqueue(
    queueName: QueueName,
    body: String,
    idempotencyKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  )

  /**
   * Enqueue the job and block waiting for the confirmation.
   */
  fun enqueueBlocking(
    queueName: QueueName,
    body: String,
    idempotencyKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  )

  /**
   * Enqueue the job and return a CompletableFuture.
   *
   * This call does not record sending metrics as it's asynchronous.
   */
  fun enqueueAsync(
    queueName: QueueName,
    body: String,
    idempotencyKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ): CompletableFuture<T>
}
