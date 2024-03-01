package misk.backoff

import java.time.Duration

/**
 * Performs exponential backoff with 100% jitter. Durations are supplied as
 * functions, so that they can change dynamically as the system is running (e.g.
 * in response to changes in dynamic flags)
 */
class FullJitterBackoff(
  baseDelay: () -> Duration,
  maxDelay: () -> Duration,
) : ExponentialBackoff(
  baseDelay,
  maxDelay,
  { curDelayMs -> Duration.ofMillis(curDelayMs + 1) }
)
