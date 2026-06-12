package misk.tasks

import java.time.Duration
import misk.backoff.Backoff
import misk.backoff.ExponentialBackoff

data class RepeatedTaskQueueConfig
@JvmOverloads
constructor(
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
   * If -1 then an unbounded number of parallel tasks are allowed. An unbounded number of tasks can be useful for an App
   * that needs to dynamically compute the number of tasks at runtime. However, the App is then responsible for ensuring
   * an upper bound for the number of tasks submitted.
   */
  val num_parallel_tasks: Int = 1,

  /**
   * If true, [RepeatedTaskQueue.triggerShutdown] will call [java.util.concurrent.ExecutorService.shutdownNow] on the
   * underlying task executor, which interrupts any worker threads currently running a task lambda. This is useful for
   * tasks that perform long-blocking I/O (for example, an SQS long-poll) and would otherwise prevent
   * [com.google.common.util.concurrent.Service.awaitTerminated] from returning until the I/O completes on its own.
   *
   * Defaults to false to preserve historical behavior. Only enable this if your task lambda either propagates
   * [InterruptedException] (or its wrappers, such as AWS SDK's `AbortedException`) or otherwise tolerates being
   * interrupted mid-execution. Tasks that swallow [InterruptedException] without surfacing it will have their work
   * silently abandoned at shutdown.
   *
   * When enabled, the [RepeatedTaskQueue] catches [InterruptedException] thrown out of the task lambda, restores the
   * thread's interrupt flag, and treats the result as [Status.NO_RESCHEDULE].
   */
  val interrupt_on_shutdown: Boolean = false,
) {

  /** Construct an [ExponentialBackoff] from the initial delay using the default configs. */
  fun defaultBackoff(initialDelay: Duration): Backoff {
    return ExponentialBackoff(
      initialDelay,
      Duration.ofSeconds(default_max_delay_sec),
      Duration.ofMillis(default_jitter_ms),
    )
  }
}
