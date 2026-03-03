package misk.backoff

import java.time.Duration

/** Calculates how long to backoff on a retry. [Backoff]s are stateful and not thread-safe */
interface Backoff {
  /** Resets the backoff, typically when a request has succeeded  */
  fun reset()

  /** @return Determines the amount of time to wait before the next retry. */
  fun nextRetry(): Duration
}
