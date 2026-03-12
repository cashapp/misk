package misk.backoff

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Retries the provided function up to a certain number of times, applying the given backoff between each retry. If
 * provided, the onRetry callback is called when a retry happens, allowing clients to perform a task (log, emit metrics)
 * every time a retry occurs. The retry function is provided with current retry count, in case this is relevant.
 */
@JvmOverloads
@Deprecated(
  "Use retry(config: RetryConfig) instead",
  replaceWith = ReplaceWith("retry(RetryConfig.Builder(upTo - 1, withBackoff).build(), block)"),
)
fun <A> retry(
  upTo: Int,
  withBackoff: Backoff,
  onRetry: ((retryCount: Int, exception: Exception) -> Unit)? = null,
  block: (retryCount: Int) -> A,
): A {
  // Convert old upTo (total attempts) to new maxRetries (retries after initial)
  val retryConfig = RetryConfig.Builder(upTo - 1, withBackoff)
  if (onRetry != null) {
    retryConfig.onRetry(onRetry)
  }
  return retry(retryConfig.build(), block)
}

/**
 * Retries the provided function, applying the given backoff between each retry.
 *
 * @param config Configuration specifying maxRetries (number of retries after the initial attempt),
 *               backoff strategy, and retry conditions.
 * @param block The function to execute. Receives the attempt number (0 = initial, 1+ = retries).
 */
fun <A> retry(config: RetryConfig, block: (retryCount: Int) -> A): A {
  config.withBackoff.reset()
  var lastException: Exception? = null
  // 0 = initial attempt, 1..maxRetries = retry attempts
  for (i in 0..config.maxRetries) {
    try {
      val result = block(i)
      config.withBackoff.reset()
      return result
    } catch (e: DontRetryException) {
      throw e
    } catch (e: Exception) {
      if (!config.shouldRetry(e)) {
        throw e
      }
      config.onRetry?.invoke(i + 1, e)
      lastException = e
      if (i < config.maxRetries) {
        try {
          Thread.sleep(config.withBackoff.nextRetry().toMillis())
        } catch (e: InterruptedException) {
          throw e
        }
      }
    }
  }
  throw lastException!!
}

/**
 * Executor that handles retries in a retryableFuture This is a single thread with the assumption that what is being
 * retried is non-blocking. This should only be handling the "retry" logic
 */
private val retryExecutor =
  Executors.newSingleThreadScheduledExecutor { Thread(it, "retry-scheduler").apply { isDaemon = true } }

/**
 * Builds a future, that wraps the provided future returning function, that retries the provided function up to
 * [RetryConfig.maxRetries] times after the initial attempt, applying the given backoff between each retry.
 * This will use the [retryExecutor] to retry the block when handling a failure. If provided, the onRetry callback
 * is called when a retry happens, allowing clients to perform a task (log, emit metrics) every time a retry occurs.
 * The retry function is provided with current attempt number, in case this is relevant.
 */
fun <A> retryableFuture(config: RetryConfig, block: (retryCount: Int) -> CompletableFuture<A>): CompletableFuture<A> {
  val attemptLimit = config.maxRetries + 1

  fun attempt(remaining: Int): CompletableFuture<A> {
    val attemptNumber = attemptLimit - remaining // zero based
    return block(attemptNumber)
      .handle { result, throwable ->
        val exception = throwable?.let { (it as? Exception) ?: throw throwable }
        if (exception == null) {
          config.withBackoff.reset()
          CompletableFuture.completedFuture(result)
        } else if (config.shouldRetry(exception) && remaining > 1) {
          config.onRetry?.invoke(attemptNumber + 1, exception)
          val retry = CompletableFuture<A>()
          retryExecutor.schedule(
            {
              attempt(remaining - 1).whenComplete { remaining, throwable ->
                if (throwable == null) retry.complete(remaining) else retry.completeExceptionally(throwable)
              }
            },
            config.withBackoff.nextRetry().toMillis(),
            TimeUnit.MILLISECONDS,
          )
          retry
        } else {
          CompletableFuture.failedFuture(exception)
        }
      }
      .thenCompose { it }
  }
  config.withBackoff.reset()
  return attempt(attemptLimit)
}

/**
 * Configuration for retry behavior.
 *
 * @property maxRetries The maximum number of retries after the initial attempt.
 * @property withBackoff The backoff strategy to use between retries.
 * @property onRetry Optional callback invoked on each retry with the retry count and exception.
 * @property shouldRetry Predicate to determine if an exception should trigger a retry.
 */
class RetryConfig
private constructor(
  val maxRetries: Int,
  val withBackoff: Backoff,
  val onRetry: ((retryCount: Int, exception: Exception) -> Unit)?,
  val shouldRetry: (e: Exception) -> Boolean,
) {
  /** @deprecated Use [maxRetries] instead. This returns maxRetries + 1 for backwards compatibility. */
  @Deprecated("Use maxRetries instead", replaceWith = ReplaceWith("maxRetries"))
  val upTo: Int get() = maxRetries + 1

  // @JvmOverloads is intentionally omitted: it doesn't work correctly when the required parameter
  // (withBackoff) is between optional parameters (maxRetries, upTo).
  @Suppress("AnnotatePublicApisWithJvmOverloads")
  class Builder constructor(
    maxRetries: Int? = null,
    val withBackoff: Backoff,
    upTo: Int? = null,
  ) {
    val maxRetries: Int = maxRetries ?: (upTo?.let { it - 1 } ?: error("Either maxRetries or upTo must be specified"))

    /** @deprecated Use [maxRetries] instead. This returns maxRetries + 1 for backwards compatibility. */
    @Deprecated("Use maxRetries instead", replaceWith = ReplaceWith("maxRetries"))
    val upTo: Int get() = this.maxRetries + 1

    var onRetry: ((retryCount: Int, exception: Exception) -> Unit)? = null
    var shouldRetry: (e: Exception) -> Boolean = { true }

    fun onRetry(onRetry: (retryCount: Int, exception: Exception) -> Unit) = apply { this.onRetry = onRetry }

    fun shouldRetry(shouldRetry: (e: Exception) -> Boolean) = apply { this.shouldRetry = shouldRetry }

    fun build(): RetryConfig {
      require(maxRetries >= 0) { "maxRetries must be non-negative" }
      return RetryConfig(maxRetries, withBackoff, onRetry, shouldRetry)
    }
  }
}

class DontRetryException : Exception {
  constructor(message: String? = null) : super(message)

  constructor(cause: Exception?) : super(cause)

  constructor(message: String?, cause: Exception?) : super(message, cause)
}
