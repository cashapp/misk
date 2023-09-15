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
  f: (retryCount: Int) -> A,
  ): A {
  require(upTo > 0) { "must support at least one call" }

  withBackoff.reset()

  var lastException: Exception? = null

  for (i in 0 until upTo) {
    try {
      val result = f(i)
      withBackoff.reset()
      return result
    } catch (e: DontRetryException) {
      throw e
    } catch (e: Exception) {
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

class DontRetryException(message: String, exception: Exception?) : Exception(message, exception) {
  constructor(message: String) : this(message, null)
}
