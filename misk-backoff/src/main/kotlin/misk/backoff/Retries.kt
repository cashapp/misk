package misk.backoff

/**
 * Retries the provided function up to a certain number of times, applying the given backoff
 * between each retry. If provided, the onRetry callback is called when a retry happens, allowing
 * clients to perform a task (log, emit metrics) every time a retry occurs.
 * The retry function is provided with current retry count, in case this is relevant.
 */
@JvmOverloads
@Deprecated("Use retry(config: RetryConfig) instead",
  replaceWith = ReplaceWith("retry(RetryConfig.Builder(upTo, withBackoff).build(), block)")
)
fun <A> retry(
  upTo: Int,
  withBackoff: Backoff,
  onRetry: ((retryCount: Int, exception: Exception) -> Unit)? = null,
  block: (retryCount: Int) -> A,
): A {
  val retryConfig = RetryConfig.Builder(upTo, withBackoff)
  if (onRetry != null) {
    retryConfig.onRetry(onRetry)
  }
  return retry(retryConfig.build(), block)
}

fun <A> retry(
  config: RetryConfig,
  block: (retryCount: Int) -> A,
): A {
  config.withBackoff.reset()
  var lastException: Exception? = null
  for (i in 0 until config.upTo) {
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
      if (i + 1 < config.upTo) {
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

class RetryConfig private constructor(
  val upTo: Int,
  val withBackoff: Backoff,
  val onRetry: ((retryCount: Int, exception: Exception) -> Unit)?,
  val shouldRetry: (e: Exception) -> Boolean,
) {
  data class Builder(
    val upTo: Int,
    val withBackoff: Backoff
  ) {
    var onRetry: ((retryCount: Int, exception: Exception) -> Unit)? = null
    var shouldRetry: (e: Exception) -> Boolean = { true }

    fun onRetry(onRetry: (retryCount: Int, exception: Exception) -> Unit) = apply { this.onRetry = onRetry }
    fun shouldRetry(shouldRetry: (e: Exception) -> Boolean) = apply { this.shouldRetry = shouldRetry }

    fun build() : RetryConfig {
      require(upTo > 0) { "must support at least one call" }
      return RetryConfig(upTo, withBackoff, onRetry, shouldRetry)
    }
  }
}

class DontRetryException : Exception {
  constructor(message: String? = null) : super(message)
  constructor(cause: Exception?) : super(cause)
  constructor(message: String?, cause: Exception?) : super(message, cause)
}
