package misk.time

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import java.time.Duration

fun <T> timed(f: () -> T) = timed(Ticker.systemTicker(), f)

fun <T> timed(ticker: Ticker, f: () -> T): Pair<Duration, T> {
  val stopwatch = Stopwatch.createStarted(ticker)
  val result = f()
  return stopwatch.stop().elapsed() to result
}
