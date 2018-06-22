package misk.backoff

/**
 * Retries the provided function up to a certain number of times, applying the given backoff
 * between each retry. The retry function is provided with current retry count, in case this is
 * relevant.
 *
 * retryException returns true if the caught exception should be retried, subject to the limit.
 * By default all exceptions are retried.
 */
fun <A> retry(
  upTo: Int,
  withBackoff: Backoff,
  retryException: ((e: Exception) -> Boolean)? = null,
  f: (retryCount: Int) -> A
): A {
  require(upTo > 0) { "must support at least one call" }

  withBackoff.reset()

  var lastException: Exception? = null

  for (i in 0 until upTo) {
    try {
      val result = f(i)
      withBackoff.reset()
      return result
    } catch (e: Exception) {
      if (retryException?.invoke(e) == false) throw e
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
