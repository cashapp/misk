package misk.jobqueue

import java.time.Duration

/**
 * A [JobQueue] enqueues jobs for asynchronous execution, possibly in another process. Jobs
 * are enqueued immediately and may involve an RPC to the underlying job queueing system, so should
 * not be done from within a database transaction. Applications that need to enqueue jobs
 * atomically with a local database transaction should use the [TransactionalJobQueue] interface
 */
interface JobQueue {
  /**
   * Enqueue a job onto the given queue, along with a set of job attributes.
   *
   * @param queueName the name of the queue on which to place the job
   * @param idempotenceKey Allows consumers to filter duplicate messages if the underlying job
   * queueing system provides at least once delivery.
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * @param attributes Arbitrary contextual attributes associated with the job
   */
  fun enqueue(
    queueName: QueueName,
    idempotenceKey: String,
    body: String,
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf()
  )

  /**
   * Enqueue a job onto the given queue, along with a set of job attributes.
   *
   * @param queueName the name of the queue on which to place the job
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * @param attributes Arbitrary contextual attributes associated with the job
   */
  // Deprecated. Provide an idempotence key.
  fun enqueue(
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf()
  )
}
