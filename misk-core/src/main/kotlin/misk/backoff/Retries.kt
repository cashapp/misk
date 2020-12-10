package misk.backoff

/**
 * Retries the provided function up to a certain number of times, applying the given backoff
 * between each retry. The retry function is provided with current retry count, in case this is
 * relevant.
 * @param upTo The number of times to retry the function.
 * @param withBackoff The backoff schedule to retry function.
 * @param f The function to be retried.
 * @param onlyRetry A set of Exceptions to be retried. If list is non-null, only the given
 * Exceptions will be retried, all others will throw.
 */
fun <A> retry(
  upTo: Int,
  withBackoff: Backoff,
  onlyRetry: List<Exception> = emptyList(),
  f: (retryCount: Int) -> A
): A {
  require(upTo > 0) { "must support at least one call" }

  withBackoff.reset()

  var lastException: Exception? = null
  val onlyRetryClasses = onlyRetry.map { it.javaClass }.toSet()

  for (i in 0 until upTo) {
    try {
      val result = f(i)
      withBackoff.reset()
      return result
    } catch (e: DontRetryException) {
      throw e
    } catch (e: Exception) {
      if (onlyRetryClasses.isNotEmpty() && e.javaClass !in onlyRetryClasses) {
        throw e
      }
      
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
