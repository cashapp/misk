package misk.tasks

import java.time.Duration

enum class Status {
  /** The task completed successfully and processed work */
  OK,

  /** The task had no work to complete */
  NO_WORK,

  /** The task resulted in an error */
  FAILED,

  /** The task should not be rescheduled */
  NO_RESCHEDULE
}

data class Result(val status: Status, val nextDelay: Duration)
