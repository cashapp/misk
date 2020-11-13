package misk.backoff

/**
 * Retries the provided function up to a certain number of times, applying the given backoff
 * between each retry. The retry function is provided with current retry count, in case this is
 * relevant
 */
fun <A> retry(upTo: Int, withBackoff: Backoff, f: (retryCount: Int) -> A): A {
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

class DontRetryException(message: String) : Exception(message)
