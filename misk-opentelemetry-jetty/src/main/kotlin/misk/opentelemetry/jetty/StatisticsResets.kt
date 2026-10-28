package misk.opentelemetry.jetty

import java.util.function.LongSupplier

/**
 * Detects `StatisticsHandler` resets (Jetty resets its statistics whenever its server starts) from the time the
 * statistics started: [clockMs], the clock the handler measures its duration on, minus [statisticsDurationMs]. That
 * start only moves on a reset. A step of that clock (Jetty 11 measures `statsOnMs` on the wall clock) shifts the clock
 * and the duration together, and pauses between reads change neither, so neither looks like a reset however young the
 * statistics are. A reset is reported when the start moved forward by more than [RESET_TOLERANCE_MS].
 *
 * Jetty 11 and 12 restart the duration before clearing the totals, so callers read the duration, then the total, then
 * the duration again ([consistent]); a duration that went backwards means a reset began during the read, so the total
 * is read again. A read landing between Jetty's two steps can still pair a fresh duration with a not-yet-cleared total;
 * that window is a few instructions long and only opens when a handler is reset with totals already recorded. Not
 * thread-safe; callers synchronize.
 */
internal class StatisticsResets(private val statisticsDurationMs: LongSupplier, private val clockMs: LongSupplier) {
  private var lastStartMs: Long? = null
  private var sampledStartMs = 0L
  private var sampleSpreadMs = 0L

  /** The statistics duration to read before the total; also samples when the statistics started. */
  fun durationMs(): Long {
    val before = clockMs.asLong
    val durationMs = statisticsDurationMs.asLong
    sampleSpreadMs = clockMs.asLong - before
    sampledStartMs = before - durationMs
    return durationMs
  }

  /**
   * True when the total read after [durationBeforeMs] belongs to the same statistics generation and the start sample is
   * precise; a duration that went backwards is a reset, or a clock step back, during the read.
   */
  fun consistent(durationBeforeMs: Long): Boolean = precise() && statisticsDurationMs.asLong >= durationBeforeMs

  /**
   * True when the statistics were reset since the previous call; the first call never reports a reset. A start sample
   * that stayed imprecise through every retry (the clock stepped, either way, while it was taken) is ignored.
   */
  fun resetSinceLastRead(): Boolean {
    if (!precise()) return false
    val start = sampledStartMs
    val previous = lastStartMs
    lastStartMs = start
    return previous != null && start - previous > RESET_TOLERANCE_MS
  }

  private fun precise(): Boolean = sampleSpreadMs in 0..MAX_SAMPLE_SPREAD_MS

  companion object {
    /** Reads to attempt before accepting a sample a reset or clock step may have straddled. */
    const val MAX_READ_ATTEMPTS = 3

    /** Start movement larger than any sampling error, so only a reset exceeds it. */
    private const val RESET_TOLERANCE_MS = 100L

    /** Start samples are off by at most this, the time spent reading the duration between two clock reads. */
    private const val MAX_SAMPLE_SPREAD_MS = 50L
  }
}
