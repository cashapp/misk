package misk.concurrent

import com.google.common.util.concurrent.MoreExecutors
import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.ExecutorService
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScheduledExecutorService for testing that runs in the current thread and is triggered using the
 * `tick()` method. An injected [Clock] is used to decide whether to execute a scheduled task.
 *
 * This service must always "catch up" to the clock, so fixed rate and fixed delay jobs are not different.
 */
@Singleton
class FakeScheduledExecutorService @Inject constructor(private val clock: Clock) :
    ScheduledExecutorService, ExecutorService by MoreExecutors.newDirectExecutorService() {

  internal val futures = PriorityBlockingQueue<ScheduledFutureTask<*>>()

  private var shutdown = false

  /** Check the current time on the clock and run any scheduled tasks that are due. */
  fun tick() {
    var future = futures.poll()
    while (future != null) {
      if (clock.millis() < future.executeAt) {
        // We went too far. Requeue this future, it's not yet ready to run.
        futures.add(future)
        return
      }

      if (!future.isRepeated) {
        future.run()
      } else {
        future.runAndReset()
        future.reschedule()
      }
      future = futures.poll()
    }
  }

  override fun shutdown() {
    shutdown = true
  }

  override fun isShutdown(): Boolean = shutdown

  override fun schedule(
    command: Runnable,
    delay: Long,
    unit: TimeUnit
  ) = scheduleTask(delay, unit) { command.run() }

  override fun <V> schedule(
    callable: Callable<V>,
    delay: Long,
    unit: TimeUnit
  ) = scheduleTask(delay, unit) { callable.call() }

  private fun <V> scheduleTask(delay: Long, unit: TimeUnit, task: () -> V): ScheduledFuture<V> {
    val executeAt = clock.millis() + unit.toMillis(delay)
    val future = ScheduledFutureTask(executeAt, clock, task)
    futures.add(future)
    return future
  }

  override fun scheduleAtFixedRate(
    command: Runnable,
    initialDelay: Long,
    period: Long,
    unit: TimeUnit
  ): ScheduledFuture<*> {
    check(period > 0) { "Period ($period) must be greater than 0" }
    return scheduleWithFixedDelay(command, initialDelay, period, unit)
  }

  override fun scheduleWithFixedDelay(
    command: Runnable,
    initialDelay: Long,
    delay: Long,
    unit: TimeUnit
  ): ScheduledFuture<*> {
    check(delay > 0) { "Delay ($delay) must be greater than 0" }

    val executeAt = clock.millis() + unit.toMillis(initialDelay)
    val everyMillis = unit.toMillis(delay)

    val future = ScheduledFutureTask(executeAt, everyMillis, clock) { command.run() }
    futures.add(future)
    return future
  }

  inner class ScheduledFutureTask<V>(
    var executeAt: Long,
    private val fixedDelay: Long,
    val clock: Clock,
    val task: () -> V
  ) : FutureTask<V>(task), ScheduledFuture<V> {
    constructor(
      executeAt: Long,
      clock: Clock,
      task: () -> V
    ) : this(executeAt, 0L, clock, task)

    override fun compareTo(other: Delayed): Int =
        getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))

    override fun getDelay(unit: TimeUnit) =
        unit.convert(clock.millis() - executeAt, TimeUnit.MILLISECONDS)

    val isRepeated: Boolean get() = fixedDelay > 0

    public override fun runAndReset(): Boolean {
      return super.runAndReset()
    }

    internal fun reschedule() {
      check(isRepeated) { "Cannot reschedule a non-repeated FutureTask" }
      futures.add(ScheduledFutureTask(
          executeAt = executeAt + fixedDelay,
          fixedDelay = fixedDelay,
          clock = clock,
          task = task
      ))
    }
  }
}
