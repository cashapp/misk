package misk.jdbc.retry

/**
 * Default retry configuration values shared across transacter implementations.
 */
object RetryDefaults {
  /** Default number of retries after the initial attempt. */
  const val MAX_RETRIES: Int = 2
  const val MIN_RETRY_DELAY_MILLIS: Long = 100
  const val MAX_RETRY_DELAY_MILLIS: Long = 500
  const val RETRY_JITTER_MILLIS: Long = 400
}
