package misk.tasks

import java.time.Duration

enum class Status(private val metricLabel: String) {
  /** The task completed successfully and processed work */
  OK("ok"),

  /** The task had no work to complete */
  NO_WORK("no_work"),

  /** The task resulted in an error */
  FAILED("failed"),

  /** The task should not be rescheduled */
  NO_RESCHEDULE("no_reschedule");

  /**
   * The metric label for the status. This is used instead of name() in case the code is refactored.
   */
  fun metricLabel(): String {
    return metricLabel
  }
}

data class Result(val status: Status, val nextDelay: Duration)
