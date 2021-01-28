package misk.retries

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.retry

/**
 * This is a retry helper function with some hooks.
 * - [beforeRetryHook] is called before retries, but not before the first attempt.
 * Use cases:
 * - Optimistic locking where the state needs to be reloaded on retries
 * - Re-establishing a connection when a request fails due to a connection dropping
 */
suspend fun <T> retryWithHooks(
  policy: RetryPolicy<Throwable>,
  beforeRetryHook: () -> Unit,
  op: () -> T
): T {
  var numExecutions = 0
  return retry(policy) {
    if (numExecutions++ > 0) {
      beforeRetryHook()
    }
    op()
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
