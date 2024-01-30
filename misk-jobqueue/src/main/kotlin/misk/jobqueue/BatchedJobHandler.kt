package misk.jobqueue

/**
 * A [BatchedJobHandler] is an application implemented interface that handles jobs received by this
 * service. The difference between this and [JobHandler] is that this interface allows for jobs to
 * be handled in a batch. Caveats in [JobHandler] regarding idempotence, repeated delivery, and
 * the need for explicit acknowledgement or deadlettering for each job still apply.
 *
 */
interface BatchedJobHandler : AbstractJobHandler {
  fun handleJobs(jobs: Collection<Job>)
}
