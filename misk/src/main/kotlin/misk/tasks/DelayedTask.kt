package misk.tasks

import com.google.common.annotations.VisibleForTesting
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

/** A [DelayedTask] is a task that runs in  the future */
class DelayedTask(
  internal val clock: Clock,
  internal val executionTime: Instant,
  @VisibleForTesting val task: () -> Result
) : Delayed {
  override fun compareTo(other: Delayed): Int {
    return getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))
  }

  override fun getDelay(unit: TimeUnit): Long {
    val executionTimeFromNow = Duration.between(clock.instant(), executionTime)
    return unit.convert(executionTimeFromNow.toNanos(), TimeUnit.NANOSECONDS)
  }
}
