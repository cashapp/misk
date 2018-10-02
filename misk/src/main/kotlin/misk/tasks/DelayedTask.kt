package misk.tasks

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

internal class DelayedTask(
  internal val clock: Clock,
  internal val executionTime: Instant,
  val task: () -> Result
) : Delayed {
  override fun compareTo(other: Delayed): Int {
    val timeDiff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(
        TimeUnit.MILLISECONDS)
    return Math.max(Math.min(timeDiff, Integer.MAX_VALUE.toLong()),
        Integer.MIN_VALUE.toLong()).toInt()
  }

  override fun getDelay(unit: TimeUnit): Long {
    val executionTimeFromNow = Duration.between(clock.instant(), executionTime)
    return unit.convert(executionTimeFromNow.toNanos(), TimeUnit.NANOSECONDS)
  }
}