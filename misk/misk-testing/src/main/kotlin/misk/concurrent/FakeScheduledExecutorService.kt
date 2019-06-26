package misk.concurrent

import com.google.common.collect.MultimapBuilder
import com.google.common.util.concurrent.MoreExecutors
import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.ExecutorService
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScheduledExecutorService for testing that runs in the current thread and is triggered using the
 * `tick()` method. An injected [Clock] is used to decide whether to execute a scheduled task.
 */
@Singleton
class FakeScheduledExecutorService @Inject constructor(private val clock: Clock) :
    ScheduledExecutorService, ExecutorService by MoreExecutors.newDirectExecutorService() {

  private val futures =
      MultimapBuilder.treeKeys().arrayListValues().build<Long, RunnableFuture<*>>()

  /** Check the current time on the clock and run any scheduled tasks that are due. */
  fun tick() {
    futures.entries().stream()
        .filter { entry -> entry.key <= clock.millis() }
        .forEach { entry -> entry.value.run() }
  }

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
    val future =
        ScheduledFutureTask(executeAt, clock, task)
    futures.put(executeAt, future)
    return future
  }

  override fun scheduleAtFixedRate(
    command: Runnable?,
    initialDelay: Long,
    period: Long,
    unit: TimeUnit?
  ): ScheduledFuture<*> {
    TODO("not implemented")
  }

  override fun scheduleWithFixedDelay(
    command: Runnable?,
    initialDelay: Long,
    delay: Long,
    unit: TimeUnit?
  ): ScheduledFuture<*> {
    TODO("not implemented")
  }

  class ScheduledFutureTask<V>(
    val executeAt: Long,
    val clock: Clock,
    task: () -> V
  ) : FutureTask<V>(task), ScheduledFuture<V> {
    override fun compareTo(other: Delayed): Int =
        getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))

    override fun getDelay(unit: TimeUnit) =
        unit.convert(clock.millis() - executeAt, TimeUnit.MILLISECONDS)
  }
}
