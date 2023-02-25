package misk.backoff

import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * Performs exponential backoff with optional jitter. Durations are supplied as
 * functions, so that they can change dynamically as the system is running (e.g.
 * in response to changes in dynamic flags)
 */
class ExponentialBackoff(
  private val baseDelay: () -> Duration,
  private val maxDelay: () -> Duration,
  private val jitter: () -> Duration // TODO(mmihic): Consider making jitter a %

) : Backoff {
  private var consecutiveRetryCount: Int = 0
  private var maxRetryCount = Integer.MAX_VALUE

  /**
   * Creates a new unjittered [ExponentialBackoff] using a function for the base
   * and max retry delays.
   *
   * @param baseDelay The [Supplier] for the base delay
   * @param maxDelay The [Supplier] for maximum amount of time to wait between retries
   */
  constructor(baseDelay: () -> Duration, maxDelay: () -> Duration) :
    this(baseDelay, maxDelay, { Duration.ofMillis(0) })

  /**
   * Creates a new [ExponentialBackoff] from fixed delays and jitter amounts
   *
   * @param baseDelay The base retry delay
   * @param maxDelay The max amount of time to delay
   * @param jitter The amount of jitter to introduce
   */
  constructor(baseDelay: Duration, maxDelay: Duration, jitter: Duration) :
    this({ baseDelay }, { maxDelay }, { jitter })

  /**
   * Creates a new [ExponentialBackoff] from fixed delays, without jitter
   *
   * @param baseDelay The base retry delay
   * @param maxDelay The max amount of time to delay
   */
  constructor(baseDelay: Duration, maxDelay: Duration) :
    this(baseDelay, maxDelay, Duration.ofMillis(0))

  override fun reset() {
    consecutiveRetryCount = 0
  }

  override fun nextRetry(): Duration {
    // Cap the retry count to prevent long overflow. Since it's a function of the configurable base
    // delay, we just wait for the maxDelay to be reached and memoize the current retry count.
    consecutiveRetryCount = Math.min(consecutiveRetryCount + 1, maxRetryCount)
    val backOff = Math.pow(2.0, (consecutiveRetryCount - 1).toDouble()).toLong()
    val maxDelayMs = maxDelay().toMillis()
    val delayMs = Math.min(maxDelayMs, baseDelay().toMillis() * backOff)
    if (delayMs == maxDelayMs) {
      maxRetryCount = consecutiveRetryCount
    }
    return Duration.ofMillis(delayMs + randomJitter())
  }

  private fun randomJitter(): Long {
    val maxJitterMs = jitter().toMillis()
    return if (maxJitterMs == 0L) 0
    else Math.floorMod(ThreadLocalRandom.current().nextLong(), maxJitterMs)
  }
}
