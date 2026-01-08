package wisp.ratelimiting.bucket4j

import io.github.bucket4j.TimeMeter
import java.time.Clock

class ClockTimeMeter(private val clock: Clock) : TimeMeter {
  override fun currentTimeNanos() = clock.millis() * 1_000_000L

  override fun isWallClockBased() = true
}
