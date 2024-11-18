package misk.backoff

import java.time.Duration

class FlatBackoff @JvmOverloads constructor(val duration: Duration = Duration.ofMillis(0)) : Backoff {
  override fun reset() {}
  override fun nextRetry() = duration
}
