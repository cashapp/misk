package misk.tasks

import misk.backoff.Backoff
import misk.backoff.ExponentialBackoff
import java.time.Duration

data class RepeatedTaskQueueConfig(
  /**
   * The default amount of jitter to use when scheduling backoffs.
   *
   * Can be overridden when scheduling a tasks.
   */
  val default_jitter_ms: Long = 50,

  /**
   * The default maximum backoff time.
   *
   * Can be overridden when scheduling a task.
   */
  val default_max_delay_sec: Long = 60,

  /**
   * The number of parallel tasks to run.
   */
  val num_parallel_tasks: Int = 1

) {

  /**
   * Construct an [ExponentialBackoff] from the initial delay using the default configs.
   */
  fun defaultBackoff(initialDelay: Duration): Backoff {
    return ExponentialBackoff(
        initialDelay,
        Duration.ofSeconds(default_max_delay_sec),
        Duration.ofMillis(default_jitter_ms))
  }
}
