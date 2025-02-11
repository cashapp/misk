package misk.jobqueue.v2

import misk.jobqueue.QueueName

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
}
