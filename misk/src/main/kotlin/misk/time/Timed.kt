package misk.time

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import java.time.Duration
import java.util.concurrent.TimeUnit

fun <T> timed(f: () -> T) = timed(Ticker.systemTicker(), f)

fun <T> timed(ticker: Ticker, f: () -> T): Pair<Duration, T> {
  val stopwatch = Stopwatch.createStarted(ticker)
  val result = f()
  return Duration.ofMillis(stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)) to result
}
