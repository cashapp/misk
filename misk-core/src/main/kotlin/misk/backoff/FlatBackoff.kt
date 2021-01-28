package misk.backoff

import java.time.Duration

@Deprecated("Use kotlinRetry instead")
class FlatBackoff(val duration: Duration = Duration.ofMillis(0)) : Backoff {
  override fun reset() {}
  override fun nextRetry() = duration
}
