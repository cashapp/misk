package wisp.task

import wisp.config.Config

data class RepeatedTaskConfig(
  /**
   * Time between runs of tasks, set to 0 if immediately repeat task
   */
  val timeBetweenRunsMs: Long = 30000L,

  /**
   * Initial delay before starting the first task run, defaults to no delay,
   * i.e. immediate start
   */
  val initialDelayMs: Long = 0L,

  /**
   * The default amount of jitter to use when scheduling backoffs.
   */
  val defaultJitterMs: Long = 50L,

  /**
   * The default maximum backoff time.
   */
  val defaultMaxDelayMs: Long = 60000L,

) : Config
