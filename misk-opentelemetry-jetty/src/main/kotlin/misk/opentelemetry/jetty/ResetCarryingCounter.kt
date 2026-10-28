package misk.opentelemetry.jetty

import java.util.function.DoubleSupplier
import java.util.function.LongSupplier

/**
 * Keeps a cumulative Jetty total (time spent, bytes written) monotonic across statistics resets. Jetty resets its
 * `StatisticsHandler` totals when the server starts, while the OpenTelemetry SDK keeps the last value it observed for
 * the instrument; without carrying the previous total forward, the next delta would be negative, or would miss work if
 * the new total has already caught up.
 *
 * [statisticsDurationMs] is how long the handler has been collecting statistics, measured on [clockMs] (see
 * [StatisticsResets]); only a reset they show carries the total forward. A decrease without one is held at the previous
 * total until the total catches up: Jetty 11 records request and dispatch times from the wall clock, so a backward
 * clock step during in-flight requests can lower its time totals slightly, and carrying the lifetime total there would
 * export it twice. Totals that are Jetty `int`s use [WrappingIntCounter], which also handles 32-bit overflow.
 */
class ResetCarryingCounter(statisticsDurationMs: LongSupplier, clockMs: LongSupplier) {
  private val resets = StatisticsResets(statisticsDurationMs, clockMs)
  private var lastValue = 0.0
  private var base = 0.0

  @Synchronized
  fun extend(raw: DoubleSupplier): Double {
    var durationMs: Long
    var value: Double
    var attempts = 0
    do {
      durationMs = resets.durationMs()
      value = raw.asDouble
    } while (!resets.consistent(durationMs) && ++attempts < StatisticsResets.MAX_READ_ATTEMPTS)
    if (resets.resetSinceLastRead()) {
      base += lastValue
      lastValue = value
    } else if (value > lastValue) {
      lastValue = value
    }
    return base + lastValue
  }

  /** Carries the total so far forward because the next read comes from a new handler whose statistics start at zero. */
  @Synchronized
  fun carry() {
    base += lastValue
    lastValue = 0.0
  }
}
