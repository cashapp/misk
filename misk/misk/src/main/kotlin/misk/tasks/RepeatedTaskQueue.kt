package misk.tasks

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractExecutionThreadService
import misk.backoff.Backoff
import misk.backoff.ExponentialBackoff
import misk.logging.getLogger
import java.time.Clock
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.DelayQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [RepeatedTaskQueue] runs repeated tasks at a user controlled rate. Internally it uses
 * a [DelayQueue] to hold the pending tasks; a background thread pulls the next task
 * from the [DelayQueue] and hands it off to an executor service for execution.
 */
class RepeatedTaskQueue @VisibleForTesting internal constructor(
  private val name: String,
  private val clock: Clock,
  private val taskExecutor: ExecutorService,
  private val dispatchExecutor: Executor?, // visible internally for testing only
  private val pendingTasks: BlockingQueue<DelayedTask> // visible internally for testing only
) : AbstractExecutionThreadService() {
  constructor(name: String, clock: Clock, taskExecutor: ExecutorService) :
      this(name, clock, taskExecutor, null, DelayQueue<DelayedTask>())

  private val running = AtomicBoolean(false)

  override fun startUp() {
    log.info { "starting repeated task queue $name" }
    running.set(true)
  }

  override fun shutDown() {
    log.info { "stopping repeated task queue $name" }

    // Remove all currently scheduled tasks, and schedule an empty task to kick the background thread
    pendingTasks.clear()
    pendingTasks.add(DelayedTask(clock, clock.instant()) {
      Result(Status.NO_RESCHEDULE, Duration.ofMillis(0))
    })
  }

  /**
   * runs the main event loop, pulling the next task from the queue and handing it off to the
   * executor for dispatching
   */
  override fun run() {
    while (running.get()) {
      // Fetch the next task, bailing out if we've shutdown
      val task = pendingTasks.take().task
      if (!running.get()) {
        return
      }

      // Hand the task off to the executor for parallel execution and repeat so long as the
      // task requests rescheduling
      taskExecutor.submit {
        val result = task()
        if (result.status != Status.NO_RESCHEDULE) schedule(result.nextDelay, task)
      }
    }
  }

  /**
   * Schedules a task to run repeatedly after an initial delay. The task itself determines the
   * next execution time
   */
  fun schedule(delay: Duration, task: () -> Result) {
    pendingTasks.add(DelayedTask(clock, clock.instant().plus(delay), task))
  }

  /**
   * Schedules a task to run repeatedly at a fixed delay, with back-off for errors and lack
   * of available work
   */
  fun scheduleWithBackoff(
    timeBetweenRuns: Duration,
    initialDelay: Duration = timeBetweenRuns,
    noWorkBackoff: Backoff = ExponentialBackoff(timeBetweenRuns, defaultMaxDelay, defaultJitter),
    failureBackoff: Backoff = ExponentialBackoff(timeBetweenRuns, defaultMaxDelay, defaultJitter),
    task: () -> Status
  ) {
    val wrappedTask: () -> Result = {
      try {
        val status = task()
        when (status) {
          Status.OK -> {
            noWorkBackoff.reset()
            failureBackoff.reset()
            Result(status, timeBetweenRuns)
          }
          Status.NO_WORK -> {
            failureBackoff.reset()
            Result(status, noWorkBackoff.nextRetry())
          }
          Status.FAILED -> {
            noWorkBackoff.reset()
            Result(status, failureBackoff.nextRetry())
          }
          Status.NO_RESCHEDULE ->
            Result(status, Duration.ofMillis(0))
        }
      } catch (th: Throwable) {
        log.error(th) { "error running repeated task on queue $name" }
        noWorkBackoff.reset()
        Result(Status.FAILED, failureBackoff.nextRetry())
      }
    }

    schedule(initialDelay, wrappedTask)
  }

  override fun serviceName() = name

  override fun executor(): Executor = dispatchExecutor ?: super.executor()

  companion object {
    private val defaultMaxDelay = Duration.ofMinutes(1)
    private val defaultJitter = Duration.ofMillis(50)
    private val log = getLogger<RepeatedTaskQueue>()
  }
}