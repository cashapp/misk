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
   * The fixed number of parallel tasks to run.
   *
   * If -1 then an unbounded number of parallel tasks are allowed. An unbounded number of tasks can
   * be useful for an App that needs to dynamically compute the number of tasks at runtime. However,
   * the App is then responsible for ensuring an upper bound for the number of tasks submitted.
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
