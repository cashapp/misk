package misk.sampling

import com.google.common.base.Ticker
import misk.concurrent.Sleeper
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * A deterministic testable rate limiter that uses two variables:
 *
 * * Permits per second. This is the application's configured rate. We express as a per-second rate
 *   but use it as a time-between-permits. For example, 250 permits per second is a permit every
 *   4 milliseconds. This may be zero, in which case all acquire attempts will return false.
 *
 * * Window size. If the application specified 250 permits per second, that doesn't specify how many
 *   permits can be returned at once. An implementation could strictly return 1 permit every 4
 *   milliseconds, or batches of 1000 permits every 4 seconds. This class hard codes the window size
 *   to 1 second. Small windows shrink batch sizes which is inefficient; large windows grow batch
 *   sizes which is bursty. This class uses 1 second to balance latency and throughput.
 *
 * The implementation tracks a future timestamp that permits are consumed until.
 *
 * This class is similar to Guava's rate limiter. Unlike Guava's rate limiter this class is testable
 * by application code using the rate limiter. It also has very predictable behavior because its
 * internal mechanisms are simpler than Guava's.
 */
class RateLimiter private constructor(
  private val ticker: Ticker,
  private val sleeper: Sleeper
) {
  @Volatile var permitsPerSecond: Long = 0L

  /**
   * The nanoTime that we've consumed all permits through. This is at most [windowSizeNs] + current
   * nanoTime.
   */
  private val atomicAllocatedUntil = AtomicLong(ticker.read())

  /** The size of our window where we can borrow bytes from the future. */
  private var windowSizeNs = TimeUnit.SECONDS.toNanos(1L)

  /**
   * Attempt to acquire [permitCount] permits, sleeping up to [timeout] if necessary for them to
   * become available.
   *
   * Returns true if permits were acquired.
   *
   * This always returns false if you request more than [1 window size][windowSizeNs] worth of
   * permits. If you need many permits, shrink your batch size. This is intended to smooth out
   * consumption of the resources guarded by this rate limiter.
   */
  fun tryAcquire(permitCount: Long, timeout: Long, unit: TimeUnit): Boolean {
    require(permitCount > 0) { "unexpected permitCount: $permitCount" }
    require(timeout >= 0L) { "unexpected timeout: $timeout" }

    val sleepTime = timeToAcquire(unit, timeout, permitCount)
      ?: return false

    if (sleepTime.toNanos() > 0L) {
      sleeper.sleep(sleepTime)
    }

    return true
  }

  /**
   * Returns the duration to sleep to acquire [permitCount], or null if the permits cannot be
   * acquired within the given timeout.
   *
   * This implementation is lock-free.
   *
   * @return the time to wait, never greater than [timeout]. Null to not wait because permits were
   *     not issued.
   */
  private fun timeToAcquire(
    unit: TimeUnit,
    timeout: Long,
    permitCount: Long
  ): Duration? {
    while (true) {
      val allocatedUntil = atomicAllocatedUntil.get()
      val permitsPerSecond = this.permitsPerSecond // Sample this volatile only once.

      val maxRequestSize = windowSizeNs.nanosToPermits(permitsPerSecond)
      if (permitCount > maxRequestSize) return null

      val now = ticker.read()
      val timeoutNs = unit.toNanos(timeout)

      // If this acquire succeeds, this is the time we're consuming permits through.
      val newAllocatedUntil = maxOf(allocatedUntil, now) +
        permitCount.permitsToNanos(permitsPerSecond)

      // We only sleep for permits until the beginning of our window.
      val sleepNs = newAllocatedUntil - now - windowSizeNs

      // We'd have to sleep too long for this number of permits. Fail fast.
      if (sleepNs > timeoutNs) return null

      // Try to consume permits! If atomicAllocatedUntil changed, we lost a race; loop to try again.
      if (!atomicAllocatedUntil.compareAndSet(allocatedUntil, newAllocatedUntil)) continue

      // Permits were consumed. Return how long to wait before these permits can be used.
      return if (sleepNs < 0) Duration.ZERO else Duration.ofNanos(sleepNs)
    }
  }

  fun getPermitsRemaining(
    unit: TimeUnit,
    permitCount: Long,
    timeout: Long
  ): Long {
    while (true) {
      val allocatedUntil = atomicAllocatedUntil.get()
      val permitsPerSecond = this.permitsPerSecond // Sample this volatile only once.

      val maxRequestSize = windowSizeNs.nanosToPermits(permitsPerSecond)
      if (permitCount > maxRequestSize) return 0L

      val now = ticker.read()

      // If this acquire succeeds, this is the time we're consuming permits through.
      val newAllocatedUntil = maxOf(allocatedUntil, now) +
        permitCount.permitsToNanos(permitsPerSecond)
      println(newAllocatedUntil)

      val sleepNs = newAllocatedUntil - now - windowSizeNs
      println("windowSizeNs $windowSizeNs")
      println("sleepNs $sleepNs")

      val timeoutNs = unit.toNanos(timeout)

      // We'd have to sleep too long for this number of permits. Fail fast.
      if (sleepNs > timeoutNs) return 0L

      // Try to consume permits! If atomicAllocatedUntil changed, we lost a race; loop to try again.
      if (!atomicAllocatedUntil.compareAndSet(allocatedUntil, newAllocatedUntil)) continue

      val permitsAllowed = permitCount.permitsToNanos(permitCount)
      //The total ns + time borrowed (timeoutNs) - ns remaining are converted into permits
      val permitsRemaining = (permitsAllowed + timeoutNs - newAllocatedUntil) / permitCount.permitsToNanos(permitsPerSecond)

      return if (permitsRemaining < 0L) 0L else permitsRemaining
    }
  }

  private fun Long.nanosToPermits(permitsPerSecond: Long) = this * permitsPerSecond / 1_000_000_000L

  private fun Long.permitsToNanos(permitsPerSecond: Long) = this * 1_000_000_000L / permitsPerSecond

  class Factory @Inject constructor(
    private val ticker: Ticker,
    private val sleeper: Sleeper
  ) {
    fun create(rate: Long) = RateLimiter(ticker, sleeper)
      .apply { permitsPerSecond = rate }
  }
}
