package misk.jobqueue

import java.time.Duration
import java.util.UUID

/**
 * A [JobQueue] enqueues jobs for asynchronous execution, possibly in another process. Jobs
 * are enqueued immediately and may involve an RPC to the underlying job queueing system, so should
 * not be done from within a database transaction. Applications that need to enqueue jobs
 * atomically with a local database transaction should use the [TransactionalJobQueue] interface
 */
interface JobQueue {
  fun enqueue(
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf()) =
    enqueue(queueName, body, UUID.randomUUID().toString(), deliveryDelay, attributes)

  /**
   * Enqueue a job onto the given queue, along with a set of job attributes.
   *
   * @param queueName The name of the queue on which to place the job.
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body.
   * @param idempotenceKey Client-assigned unique key, useful for application code to detect duplicate work.
   * Implementations of both [JobQueue] and [JobConsumer] are expected to _not_ perform any filtering based on this
   * value, as it carries meaning only for application code (i.e. any logic around this property should take place in
   * [JobHandler]s). Defaults to a randomly generated UUID when not explicitly set.
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * Note that depending on implementation, there may be an upper limit to this value. For instance, SQS implementation
   * limits `deliveryDelay` to 900s (15m). If a longer delay is required by applications, use the
   * [TransactionalJobQueue] interface instead.
   * @param attributes Arbitrary contextual attributes associated with the job. Implementations may limit the number of
   * attributes per message.
   */
  fun enqueue(
    queueName: QueueName,
    body: String,
    idempotenceKey: String = UUID.randomUUID().toString(),
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf())

  /**
   * Batch Enqueue a set of jobs onto the given queue, along with a set of job attributes.
   *
   * @param queueName The name of the queue on which to place the job.
   * @param jobs The set of jobs that will be enqueuedThe body of the job; See JobRequest class for
   * a full description of job parameters
   * throws [BatchEnqueueException] contains the list of jobs successfully enqueued and the list of
   * jobs that could not be enqueued. See [EnqueueErrorResult] for more details on the error information
   * returned
   */
  fun batchEnqueue(
    queueName: QueueName,
    jobs: List<JobRequest>,
  )

  /**
   * Data class containing the necessary information to be enqueued in a batch enqueue
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body.
   * @param idempotenceKey Client-assigned unique key, useful for application code to detect duplicate work.
   * Implementations of both [JobQueue] and [JobConsumer] are expected to _not_ perform any filtering based on this
   * value, as it carries meaning only for application code (i.e. any logic around this property should take place in
   * [JobHandler]s). Defaults to a randomly generated UUID when not explicitly set.
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * Note that depending on implementation, there may be an upper limit to this value. For instance, SQS implementation
   * limits `deliveryDelay` to 900s (15m). If a longer delay is required by applications, use the
   * [TransactionalJobQueue] interface instead.
   * @param attributes Arbitrary contextual attributes associated with the job. Implementations may limit the number of
   * attributes per message.
   */
  data class JobRequest(
    val body: String,
    val idempotenceKey: String = UUID.randomUUID().toString(),
    val deliveryDelay: Duration? = null,
    val attributes: Map<String, String> = mapOf())

  /**
   * Exception Data class containing the list of jobs successfully enqueued and the list of
   * jobs that could not be enqueued. See [EnqueueErrorResult] for more details on the error information
   * returned
   */
  data class BatchEnqueueException(
    val queueName: QueueName,
    val successful: List<String>,
    val failed: List<EnqueueErrorResult>
  ):Exception("Failed to enqueue all messages")

  /**
   * Data class containing the error result of a failed job enqueue
   *
   * @param idempotenceKey Client-assigned unique key for the failed enqueued job
   * @param clientError Indicates whether the error is due to a clientside error
   * @param code Error code returned by the JobQueue service
   * @param message Error message returned by the JobQueue service
   */
  data class EnqueueErrorResult(
    val idempotenceKey: String,
    val clientError: Boolean,
    val code: String,
    val message: String
  )
}
