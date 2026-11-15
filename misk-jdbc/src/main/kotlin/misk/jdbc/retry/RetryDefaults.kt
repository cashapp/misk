package misk.jdbc.retry

/**
 * Default retry configuration values shared across transacter implementations.
 */
object RetryDefaults {
  const val MAX_ATTEMPTS: Int = 3
  const val MIN_RETRY_DELAY_MILLIS: Long = 100
  const val MAX_RETRY_DELAY_MILLIS: Long = 500
  const val RETRY_JITTER_MILLIS: Long = 400
}
