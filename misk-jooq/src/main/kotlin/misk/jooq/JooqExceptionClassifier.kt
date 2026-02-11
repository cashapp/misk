package misk.jooq

import misk.jdbc.DataSourceType
import misk.jdbc.retry.DefaultExceptionClassifier
import org.jooq.exception.DataAccessException
import org.jooq.exception.DataChangedException
import java.sql.SQLException

/**
 * Exception classifier for JOOQ-specific exceptions.
 *
 * Retryable exceptions:
 * - DataChangedException: Optimistic locking failures
 * - DataAccessException: Only when the underlying cause is retryable (connection
 *   closed, SQLRecoverableException, SQLTransientException, or database-specific
 *   transient errors). Non-retryable causes like syntax errors are not retried.
 * - Falls back to DefaultExceptionClassifier for other exception types
 */
class JooqExceptionClassifier @JvmOverloads constructor(
  databaseType: DataSourceType? = null
) : DefaultExceptionClassifier(databaseType) {

  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      // DataChangedException indicates optimistic locking failure - always retry
      is DataChangedException -> true
      is DataAccessException -> {
        val cause = th.cause
        when {
          // No cause - retry for backward compatibility with existing behavior
          cause == null -> true
          // SQLException cause - only retry if actually retryable (connection issues, transient errors)
          cause is SQLException -> isMessageRetryable(cause) || isCauseRetryable(th)
          // Other cause - check the cause chain for retryable exceptions
          else -> isCauseRetryable(th)
        }
      }
      // Fall back to default classification for other exception types
      else -> super.isRetryable(th)
    }
  }
}
