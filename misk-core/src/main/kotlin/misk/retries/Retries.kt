package misk.retries

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.policy.RetryPolicy

/**
 * This is a retry helper function that calls [reader] on retries, but not on the first attempt.
 * Use cases:
 * - Optimistic locking where the state needs to be reloaded on retries
 * - Re-establishing a connection when a request fails due to a connection dropping
 */
suspend fun <T> readWriteRetry(
  /**
   * [reader] is called on retries, before [writer].
   */
  retryPolicy: RetryPolicy<Throwable>,
  reader: () -> Unit,
  writer: () -> T
    /**
     * These values are recommended by @zhxnlai.
     */
): T {
  var numExecutions = 0
  return com.github.michaelbull.retry.retry(retryPolicy) {
    if (numExecutions++ > 0) {
      reader()
    }
    writer()
  }
}

/**
 * Returns a [RetryPolicy] that will throw the error that triggered the retry if it is type [T].
 * Otherwise, it voices no objection to retrying.
 */
inline fun <reified T : Exception> doNotRetry(): RetryPolicy<Throwable> {
  return {
    if (this.reason is T) {
      throw this.reason
    }

    ContinueRetrying
  }
}
