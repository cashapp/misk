package misk.jobqueue.v2

enum class JobStatus {
  OK,
  RETRY_LATER,
  DEAD_LETTER,
}
