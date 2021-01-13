package misk.jobqueue

/** Incoming job */
interface Job {
  /** name of the queue on which the job was received */
  val queueName: QueueName

  /** system assigned globally unique id for the job */
  val id: String

  /**
   * Application assigned key for a job.
   *
   * @see JobQueue.enqueue
   */
  val idempotenceKey: String

  /** body of the job */
  val body: String

  /** context attributes associated with the job */
  val attributes: Map<String, String>

  /**
   * Acknowledges the job and deletes it from the underlying queue. May perform an RPC, and thus
   * should not be called while holding database transactions or other resources
   */
  fun acknowledge()

  /** Moves the job from the main queue onto the associated dead letter queue. May perform an RPC */
  fun deadLetter()
}
