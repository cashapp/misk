package misk.opentelemetry.jetty

import java.util.function.IntSupplier
import java.util.function.LongSupplier

/**
 * Widens a cumulative Jetty `int` counter that wraps at 2^32 (reading as negative after 2^31) into a monotonic long.
 * Both Jetty 11 and Jetty 12 `StatisticsHandler` expose their request and response totals as `int`.
 *
 * [statisticsDurationMs] is how long the handler has been collecting statistics, measured on [clockMs]; when they show
 * a reset (see [StatisticsResets]) the total seen so far is carried forward, even if the new total has already caught
 * up. A wrap only follows a value in the upper half of the unsigned range, so a drop from below 2^31 is also treated as
 * a reset in case the check missed one. Use [neverReset] for totals Jetty does not clear on reset. Requires at least
 * one read per 2^31 increments, which the export interval guarantees.
 */
class WrappingIntCounter(statisticsDurationMs: LongSupplier, clockMs: LongSupplier) {
  private val resets = StatisticsResets(statisticsDurationMs, clockMs)
  private var lastValue = 0L
  private var base = 0L

  @Synchronized
  fun extend(raw: IntSupplier): Long {
    var durationMs: Long
    var value: Long
    var attempts = 0
    do {
      durationMs = resets.durationMs()
      value = Integer.toUnsignedLong(raw.asInt)
    } while (!resets.consistent(durationMs) && ++attempts < StatisticsResets.MAX_READ_ATTEMPTS)
    if (resets.resetSinceLastRead() || (value < lastValue && lastValue < WRAP_THRESHOLD)) {
      base += lastValue
    } else if (value < lastValue) {
      base += 1L shl 32
    }
    lastValue = value
    return base + value
  }

  /** Carries the total so far forward because the next read comes from a new handler whose statistics start at zero. */
  @Synchronized
  fun carry() {
    base += lastValue
    lastValue = 0L
  }

  companion object {
    /** A 32-bit wrap only follows a value in the upper half of the unsigned range. */
    private const val WRAP_THRESHOLD = 1L shl 31

    private val FIXED = LongSupplier { 0L }

    /** A counter for totals that statistics resets leave untouched, so only wraps are handled. */
    @JvmStatic fun neverReset(): WrappingIntCounter = WrappingIntCounter(FIXED, FIXED)
  }
}
