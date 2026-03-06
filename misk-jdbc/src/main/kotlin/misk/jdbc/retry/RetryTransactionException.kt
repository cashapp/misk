package misk.jdbc.retry

/**
 * An exception that signals that the current transaction should be retried.
 * This can be thrown by application code to trigger a retry without indicating a failure.
 */
open class RetryTransactionException @JvmOverloads constructor(
  message: String? = null,
  cause: Throwable? = null
) : Exception(message, cause)
