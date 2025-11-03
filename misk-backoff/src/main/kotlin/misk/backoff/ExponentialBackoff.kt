package misk.backoff

import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * Performs exponential backoff with optional jitter. Durations are supplied as
 * functions, so that they can change dynamically as the system is running (e.g.
 * in response to changes in dynamic flags)
 */
open class ExponentialBackoff @JvmOverloads constructor(
  private val baseDelay: () -> Duration,
  private val maxDelay: () -> Duration,
  private val jitter: () -> Duration,
  // Takes the next retry delay as an argument and returns the amount of jitter to introduce
  private val jitterFromNextDelay: (Long) -> Duration = { jitter() },
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
    this(baseDelay, maxDelay, { Duration.ZERO }, { Duration.ZERO })

  /**
   * Creates a new jittered [ExponentialBackoff] using a function for the base
   * and max retry delays, and a function for the jitter amount.
   *
   * @param baseDelay The [Supplier] for the base delay
   * @param maxDelay The [Supplier] for maximum amount of time to wait between retries
   * @param jitterFromNextDelay The [Supplier] for maximum amount of time to wait between retries
   */
  constructor(
    baseDelay: () -> Duration,
    maxDelay: () -> Duration,
    jitterFromNextDelay: (Long) -> Duration
  ) :
    this(baseDelay, maxDelay, { Duration.ZERO }, jitterFromNextDelay)

  /**
   * Creates a new jittered [ExponentialBackoff] from fixed delays and jitter amounts and a function
   * for the jitter.
   *
   * @param baseDelay The base retry delay
   * @param maxDelay The max amount of time to delay
   * @param jitterFromNextDelay The [Supplier] for maximum amount of time to wait between retries
   */
  constructor(
    baseDelay: Duration,
    maxDelay: Duration,
    jitterFromNextDelay: (Long) -> Duration
  ) :
    this({ baseDelay }, { maxDelay }, { Duration.ZERO }, jitterFromNextDelay)

  /**
   * Creates a new [ExponentialBackoff] from fixed delays and jitter amounts
   *
   * @param baseDelay The base retry delay
   * @param maxDelay The max amount of time to delay
   * @param jitter The amount of jitter to introduce
   */
  constructor(baseDelay: Duration, maxDelay: Duration, jitter: Duration) :
    this({ baseDelay }, { maxDelay }, { jitter }, { jitter })

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
    return Duration.ofMillis(delayMs + randomJitter(delayMs))
  }

  private fun randomJitter(curDelayMs: Long): Long {
    val maxJitterMs = jitterFromNextDelay(curDelayMs).toMillis()
    return if (maxJitterMs == 0L) 0
    else Math.floorMod(ThreadLocalRandom.current().nextLong(), maxJitterMs)
  }
}
