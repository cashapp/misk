package misk.web.requestdeadlines

/**
 * Exception thrown when a request deadline has been exceeded.
 * This is used internally by the deadline propagation system to enforce timeouts.
 */
class DeadlineExceededException @JvmOverloads constructor(
  message: String = "deadline exceeded",
  cause: Throwable? = null
) : RuntimeException(message, cause)
