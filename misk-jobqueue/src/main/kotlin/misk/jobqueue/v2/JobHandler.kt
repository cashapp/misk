package misk.jobqueue.v2

sealed interface JobHandler

interface BlockingJobHandler : JobHandler{
  fun handleJob(job: Job): JobStatus
}

interface SuspendingJobHandler : JobHandler {
  suspend fun handleJob(job: Job): JobStatus
}
