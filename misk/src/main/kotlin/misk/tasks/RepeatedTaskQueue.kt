package misk.tasks

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService
import com.google.common.util.concurrent.Service
import misk.backoff.Backoff
import misk.concurrent.ExecutorServiceFactory
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.logging.getLogger
import misk.metrics.Metrics
import misk.time.timed
import java.time.Clock
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.DelayQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [RepeatedTaskQueue] runs repeated tasks at a user controlled rate. Internally it uses
 * a [DelayQueue] to hold the pending tasks; a background thread pulls the next task
 * from the [DelayQueue] and hands it off to an executor service for execution.
 *
 * [RepeatedTaskQueue] implements the [Service] interface, which requires proper startup and shutdown.
 * Alternatively, you can add new instances to the [Service] multibind.
 */
class RepeatedTaskQueue @VisibleForTesting internal constructor(
  val name: String,
  private val clock: Clock,
  private val taskExecutor: ExecutorService,
  private val dispatchExecutor: Executor?, // visible internally for testing only
  private val pendingTasks: BlockingQueue<DelayedTask>, // visible internally for testing only
  internal val metrics: RepeatedTaskQueueMetrics,
  private val config: RepeatedTaskQueueConfig = RepeatedTaskQueueConfig(),
  private val pollingTimeout: Duration = Duration.ofMillis(250)

) : AbstractExecutionThreadService() {

  private val running = AtomicBoolean(false)

  init {
    addListener(object : Service.Listener() {
      override fun starting() {
        log.info { "the background thread for repeated task queue $name is starting" }
      }

      override fun running() {
        log.info { "the background thread for repeated task queue $name is running" }
      }

      override fun stopping(from: Service.State) {
        log.info { "the background thread for repeated task queue $name is stopping" }
      }

      override fun terminated(from: Service.State) {
        log.info { "the background thread for repeated task queue $name terminated" }
      }

      override fun failed(from: Service.State, failure: Throwable) {
        log.error(failure) { "the background thread for repeated task queue $name failed" }
      }
    }, executor())
  }

  override fun startUp() {
    if (!running.compareAndSet(false, true)) return
  }

  override fun triggerShutdown() {
    // no matter what the current value, ensure we aren't running any more
    running.compareAndSet(true, false)

    log.info { "Triggered shutdown, stopping repeated task queue $name" }

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
    // N.B - If any exception escapes this method the background thread driving the repeated
    // tasks is terminated.
    while (running.get()) {
      log.info("Queue $name in run() while loop")
      // Fetch the next task, bailing out if we've shutdown
      val delayedTask = pendingTasks.poll(pollingTimeout.toMillis(), TimeUnit.MILLISECONDS)
      log.info("Queue $name in run(), task fetched, running is: ${running.get()}")
      if (!running.get()) {
        return
      }

      // get the task from the poll if it exists, otherwise get back to the start
      val task = delayedTask?.task ?: continue

      // Hand the task off to the executor for parallel execution and repeat so long as the
      // task requests rescheduling
      taskExecutor.submit {
        log.info("Queue $name in run(), task about to execute, running is: ${running.get()}")
        val result = task()
        log.info(
          "Queue $name in run(), task executed, running is: ${running.get()}, result is $result"
        )
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
      val timedResult = timed {
        try {
          task()
        } catch (th: Throwable) {
          log.error(th) { "error running repeated task on queue $name" }
          Result(Status.FAILED, retryDelayOnFailure ?: delay)
        }
      }
      metrics.taskDuration.record(
        timedResult.first.toMillis().toDouble(), name,
        timedResult.second.status.metricLabel()
      )
      timedResult.second
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
    noWorkBackoff: Backoff = config.defaultBackoff(timeBetweenRuns),
    failureBackoff: Backoff = config.defaultBackoff(timeBetweenRuns),
    task: () -> Status
  ) {
    enqueue(delay = initialDelay) {
      val timedResult = timed {
        try {
          when (val status = task()) {
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
      metrics.taskDuration.record(
        timedResult.first.toMillis().toDouble(), name,
        timedResult.second.status.metricLabel()
      )
      timedResult.second
    }
  }

  override fun serviceName() = name

  override fun executor(): Executor = dispatchExecutor ?: super.executor()

  companion object {
    private val log = getLogger<RepeatedTaskQueue>()
  }
}

@Singleton
class RepeatedTaskQueueMetrics @Inject constructor(metrics: Metrics) {
  internal val taskDuration = metrics.histogram(
    "task_queue_task_duration",
    "count and duration in ms of periodic tasks",
    listOf("name", "result")
  )
}

@Singleton
class RepeatedTaskQueueFactory @Inject constructor(
  private val clock: Clock,
  private val metrics: RepeatedTaskQueueMetrics,
  private val executorServiceFactory: ExecutorServiceFactory
) {

  /**
   * Builds a new instance of a [RepeatedTaskQueue]
   */
  fun new(
    name: String,
    config: RepeatedTaskQueueConfig = RepeatedTaskQueueConfig(),
    pollingTimeout: Duration = Duration.ofMillis(250)
  ):
    RepeatedTaskQueue {
    val executor = if (config.num_parallel_tasks == -1) {
      executorServiceFactory.unbounded("$name-%d")
    } else {
      executorServiceFactory.fixed("$name-%d", config.num_parallel_tasks)
    }
    return RepeatedTaskQueue(
      name,
      clock,
      executor,
      null,
      DelayQueue<DelayedTask>(),
      metrics,
      config,
      pollingTimeout
    )
  }

  /**
   * Builds a new instance of a [RepeatedTaskQueue] for testing
   */
  fun forTesting(
    name: String,
    backingStorage: ExplicitReleaseDelayQueue<DelayedTask>,
    pollingTimeout: Duration = Duration.ofMillis(50)
  ):
    RepeatedTaskQueue {
    val queue = RepeatedTaskQueue(
      name,
      clock,
      newDirectExecutorService(),
      executorServiceFactory.single("$name-%d"),
      backingStorage,
      metrics,
      RepeatedTaskQueueConfig(),
      pollingTimeout
    )

    // Install a status listener that will explicitly release all of the tasks from the
    // underlying delay queue backing storage at shutdown, ensuring that the termination
    // action runs and allowing the task queue itself to shutdown
    queue.addListener(object : Service.Listener() {
      override fun stopping(from: Service.State) {
        // Keep kicking the storage until backing storage is empty
        while (true) {
          backingStorage.releaseAll()
          if (backingStorage.isEmpty()) break
          Thread.sleep(500)
        }
      }
    }, newSingleThreadExecutor())

    return queue
  }
}