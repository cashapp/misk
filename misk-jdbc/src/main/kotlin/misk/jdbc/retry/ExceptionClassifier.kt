package misk.jdbc.retry

/**
 * Interface for classifying exceptions to determine if a transaction should be retried.
 */
interface ExceptionClassifier {
  /**
   * Determines if the given throwable should trigger a transaction retry.
   */
  fun isRetryable(th: Throwable): Boolean
}
