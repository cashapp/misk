package misk.jdbc.retry

/**
 * Interface for classifying exceptions as retryable or non-retryable.
 */
interface ExceptionClassifier {
  fun isRetryable(throwable: Throwable): Boolean
}
