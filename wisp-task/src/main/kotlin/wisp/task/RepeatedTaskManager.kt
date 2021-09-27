package wisp.task

import com.github.michaelbull.retry.RetryFailure
import com.github.michaelbull.retry.RetryInstruction
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import wisp.task.exception.NoTaskFoundException
import wisp.task.exception.TaskAlreadyExistsException
import java.time.Clock

/**
 * Basic management of repeated tasks.
 */
class RepeatedTaskManager(private val meterRegistry: MeterRegistry = Metrics.globalRegistry) {
  private val repeatedTasks = mutableMapOf<String, RepeatedTask>()

  /**
   * Creates the repeated task with the details supplied.  If the repeated task already
   * exists, a [TaskAlreadyExistsException] is thrown.
   *
   * Does not start the task.
   */
  fun createTask(
    name: String,
    clock: Clock = Clock.systemUTC(),
    meterRegistry: MeterRegistry = this.meterRegistry,
    repeatedTaskConfig: RepeatedTaskConfig = RepeatedTaskConfig(),
    retryPolicy: suspend RetryFailure<Throwable>.() -> RetryInstruction =
      defaultThrowableRetryPolicy +
        binaryExponentialBackoff(
          base = repeatedTaskConfig.defaultJitterMs,
          max = repeatedTaskConfig.defaultMaxDelayMs
        ),
    taskConfig: TaskConfig = TaskConfig(),
    task: (name: String, taskConfig: TaskConfig) -> Status
  ): RepeatedTask {
    if (taskExists(name)) {
      throw TaskAlreadyExistsException(name)
    }

    val repeatedTask = RepeatedTask(
      name,
      clock,
      meterRegistry,
      repeatedTaskConfig,
      retryPolicy,
      taskConfig,
      task
    )

    repeatedTasks[name] = repeatedTask
    return repeatedTask
  }

  fun getTask(taskName: String): RepeatedTask {
    return repeatedTasks[taskName] ?: throw NoTaskFoundException(taskName)
  }

  fun taskExists(taskName: String): Boolean {
    return repeatedTasks.containsKey(taskName)
  }

  /**
   * Returns true if a repeated task with the name supplied is running.
   * If the task does not exist or is not running, return false.
   */
  fun isTaskRunning(taskName: String): Boolean {
    return repeatedTasks[taskName]?.isRunning() ?: false
  }

  fun shutDown() {
    repeatedTasks.values.forEach { it.shutDown() }
  }
}
