package misk.backoff

/**
 * Retries the provided function up to a certain number of times, applying the given backoff
 * between each retry. If provided, the onRetry callback is called when a retry happens, allowing
 * clients to perform a task (log, emit metrics) every time a retry occurs.
 * The retry function is provided with current retry count, in case this is relevant.
 */
@JvmOverloads
fun <A> retry(
  upTo: Int,
  withBackoff: Backoff,
  onRetry: ((retryCount: Int, exception: Exception) -> Unit)? = null,
  shouldRetry: (e: Exception) -> Boolean = { true },
  block: (retryCount: Int) -> A,
): A {
  require(upTo > 0) { "must support at least one call" }
  withBackoff.reset()
  var lastException: Exception? = null
  for (i in 0 until upTo) {
    try {
      val result = block(i)
      withBackoff.reset()
      return result
    } catch (e: DontRetryException) {
      throw e
    } catch (e: Exception) {
      if (!shouldRetry(e)) {
        throw e
      }
      onRetry?.invoke(i + 1, e)
      lastException = e
      if (i + 1 < upTo) {
        try {
          Thread.sleep(withBackoff.nextRetry().toMillis())
        } catch (e: InterruptedException) {
          throw e
        }
      }
    }
  }
  throw lastException!!
}

class DontRetryException : Exception {
  constructor(message: String? = null) : super(message)
  constructor(cause: Exception?) : super(cause)
  constructor(message: String?, cause: Exception?) : super(message, cause)
}
