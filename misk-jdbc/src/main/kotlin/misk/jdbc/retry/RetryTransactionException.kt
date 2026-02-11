package misk.jdbc.retry

/**
 * Exception that can be thrown by application code to force a transaction retry.
 */
class RetryTransactionException @JvmOverloads constructor(
  message: String? = null,
  cause: Throwable? = null
) : RuntimeException(message, cause)
