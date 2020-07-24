package misk.jobqueue

/**
 * A [JobHandler] is an application implemented interface that handles jobs received by this
 * service. [JobHandler]s must explicitly call [Job.acknowledge] after successful processing to
 * cause the to be deleted from the underlying queue and not redelivered, or can call
 * [Job.deadLetter] to put the job onto the associated dead letter queue. The jobqueue framework
 * assumes that the underlying queueing system is at-least-once, so handlers must be prepared
 * for the possibility that a job will be delivered more than once (for example if the process
 * fails or the visibility timeout expires after processing but before acknowledgement). Typically
 * this is handled by either storing some sort of ticket in the local database when the job is
 * enqueued and deleting it as part of the application transaction when the job is processed
 * but prior to acknowledgement, or by storing some sort of "processed marker" in the local
 * database during job processing and ignoring jobs whose marker is already recorded.
 *
 */
interface JobHandler {
  fun handleJob(job: Job)
}
