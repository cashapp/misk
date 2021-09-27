package wisp.task

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.RetryFailure
import com.github.michaelbull.retry.RetryInstruction
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import kotlinx.coroutines.runBlocking
import wisp.logging.getLogger
import wisp.task.exception.FailedTaskException
import wisp.task.exception.NoWorkForTaskException
import java.time.Clock
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

/**
 * A [RepeatedTask] runs a repeated task at the user controlled rate.
 *
 * Tasks are called with a [TaskConfig] and are expected to return a [Status] or throw an
 * exception.  By default, the retry policy will try again if an exception is thrown and will
 * apply a [binaryExponentialBackoff] using the supplied [RepeatedTaskConfig]. A [Status.NO_WORK] or
 * [Status.FAILED] will be mapped to [NoWorkForTaskException] and [FailedTaskException] and thrown
 * to apply the retry policy.
 *
 * If the [Status.NO_RESCHEDULE] is returned, the [RepeatedTask] is shutdown.
 *
 * Example usage:
 *
 *     val repeatedTask = RepeatedTask("task") {
 *       // do task stuff...
 *       return Status.OK
 *     }
 *
 *     repeatedTask.startUp()
 */
class RepeatedTask(
  val name: String,
  private val clock: Clock = Clock.systemUTC(),
  private val meterRegistry: MeterRegistry = Metrics.globalRegistry,
  private val repeatedTaskConfig: RepeatedTaskConfig = RepeatedTaskConfig(),
  private val retryPolicy: suspend RetryFailure<Throwable>.() -> RetryInstruction =
    defaultThrowableRetryPolicy +
      binaryExponentialBackoff(
        base = repeatedTaskConfig.defaultJitterMs,
        max = repeatedTaskConfig.defaultMaxDelayMs
      ),
  private val taskConfig: TaskConfig = TaskConfig(),
  private val task: (name: String, taskConfig: TaskConfig) -> Status
) {
  private val running = AtomicBoolean(false)
  private var timer: Timer? = null

  fun isRunning(): Boolean {
    return running.get()
  }

  fun startUp() {
    if (running.get()) {
      log.warn { "Repeated Task $name already started!" }
      return
    }

    timer = kotlin.concurrent.timer(
      name = name,
      daemon = true,
      initialDelay = repeatedTaskConfig.initialDelayMs,
      period = repeatedTaskConfig.timeBetweenRunsMs
    ) {
      val status = runWithBackoff(retryPolicy, task)
      if (status == Status.NO_RESCHEDULE) {
        running.set(false)
        this.cancel()
      }
    }

    running.set(true)
  }

  fun shutDown() {
    running.set(false)
    timer?.cancel()
    timer = null
  }

  private fun runWithBackoff(
    retryPolicy: suspend RetryFailure<Throwable>.() -> RetryInstruction,
    task: (name: String, taskConfig: TaskConfig) -> Status
  ): Status {
    return runBlocking {
      var status = Status.OK
      retry(retryPolicy) {
        // TODO: metrics
        val startTime = clock.instant()
        val timedResult = measureTimeMillis {
          status = task(name, taskConfig)
        }
        when (status) {
          Status.NO_WORK -> {
            throw NoWorkForTaskException()
          }
          Status.FAILED -> {
            throw FailedTaskException()
          }
          else -> {
          }
        }
        status
      }
    }
  }

  companion object {
    private val log = getLogger<RepeatedTask>()
  }
}

/**
 * By default, continue retrying
 */
val defaultThrowableRetryPolicy: RetryPolicy<Throwable> = {
  ContinueRetrying
}

