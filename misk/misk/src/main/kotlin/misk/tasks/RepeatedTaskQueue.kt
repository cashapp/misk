package misk.tasks

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService
import com.google.common.util.concurrent.Service
import misk.backoff.Backoff
import misk.backoff.ExponentialBackoff
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.logging.getLogger
import java.time.Clock
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.DelayQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [RepeatedTaskQueue] runs repeated tasks at a user controlled rate. Internally it uses
 * a [DelayQueue] to hold the pending tasks; a background thread pulls the next task
 * from the [DelayQueue] and hands it off to an executor service for execution.
 *
 * [RepeatedTaskQueue] implements the [Service] interface, which requires proper startup and shutdown.
 * Alternatively, you can add new instances to the [Service] multibind.
 */
class RepeatedTaskQueue @VisibleForTesting internal constructor(
  private val name: String,
  private val clock: Clock,
  private val taskExecutor: ExecutorService,
  private val dispatchExecutor: Executor?, // visible internally for testing only
  private val pendingTasks: BlockingQueue<DelayedTask> // visible internally for testing only
) : AbstractExecutionThreadService() {
  /**
   * Creates a [RepeatedTaskQueue] backed by a real [DelayQueue], with tasks dequeued on the
   * service background thread and executed via the provided [ExecutorService]
   */
  constructor(name: String, clock: Clock, taskExecutor: ExecutorService) :
      this(name, clock, taskExecutor, null, DelayQueue<DelayedTask>())

  private val running = AtomicBoolean(false)

  override fun startUp() {
    if (!running.compareAndSet(false, true)) return

    log.info { "starting repeated task queue $name" }
  }

  override fun triggerShutdown() {
    if (!running.compareAndSet(true, false)) return

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
        // Reschedule using enqueue so as to not repeatedly wrap the task in try-catch blocks
        if (result.status != Status.NO_RESCHEDULE) enqueue(result.nextDelay, task)
      }
    }
  }

  /**
   * Schedules a task to run repeatedly after an initial delay. The task itself determines the
   * next execution time. Provide an optional retryDelayOnFailure parameter to determine when
   * the job should be retried in the case of an unhandled exception by the client
   */
  fun schedule(delay: Duration, retryDelayOnFailure: Duration? = null, task: () -> Result) {
    val wrappedTask: () -> Result = {
      try {
        task()
      } catch (th: Throwable) {
        log.error(th) { "error running repeated task on queue $name" }
        Result(Status.FAILED, retryDelayOnFailure ?: delay)
      }
    }
    enqueue(delay, wrappedTask)
  }

  private fun enqueue(delay: Duration, task: () -> Result) {
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
    schedule(delay = initialDelay) {
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
          Status.NO_RESCHEDULE -> // NB(mmihic): The delay doesn't matter since we aren't rescheduling
            Result(status, Duration.ofMillis(0))
        }
      } catch (th: Throwable) {
        log.error(th) { "error running repeated task on queue $name" }
        noWorkBackoff.reset()
        Result(Status.FAILED, failureBackoff.nextRetry())
      }
    }
  }

  override fun serviceName() = name

  override fun executor(): Executor = dispatchExecutor ?: super.executor()

  companion object {
    private val defaultMaxDelay = Duration.ofMinutes(1)
    private val defaultJitter = Duration.ofMillis(50)
    private val log = getLogger<RepeatedTaskQueue>()

    /**
     * Creates a [RepeatedTaskQueue] backed by an [ExplicitReleaseDelayQueue], allowing tests
     * to explicitly control when tasks are released for execution. Tasks are executed in a single
     * thread in the order in which they expire
     */
    @JvmStatic fun forTesting(
      name: String,
      clock: Clock,
      backingStorage: ExplicitReleaseDelayQueue<DelayedTask>
    ): RepeatedTaskQueue {
      val queue = RepeatedTaskQueue(
          name,
          clock,
          newDirectExecutorService(),
          newSingleThreadExecutor(),
          backingStorage
      )

      // Install a status listener that will explicitly release all of the tasks from the
      // underlying delay queue at shutdown, ensuring that the termination action runs and
      // allowing the task queue itself to shutdown
      val fullyTerminated = AtomicBoolean(false)
      queue.addListener(object : Service.Listener() {
        override fun stopping(from: Service.State) {
          // Keep kicking the storage until the task queue finally shuts down
          while (!fullyTerminated.get()) {
            backingStorage.releaseAll()
            Thread.sleep(500)
          }
        }

        override fun terminated(from: Service.State) {
          fullyTerminated.set(true)
        }
      }, Executors.newSingleThreadExecutor())

      return queue
    }
  }
}