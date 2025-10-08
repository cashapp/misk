package misk.jooq

import misk.jdbc.DefaultExceptionClassifier
import org.jooq.exception.DataAccessException
import org.jooq.exception.DataChangedException
import java.sql.SQLException

/**
 * Exception classifier for JOOQ-specific exceptions.
 * 
 * This extends the default classifier to handle JOOQ-specific retryable exceptions
 * like DataChangedException and DataAccessException.
 */
class JooqExceptionClassifier : DefaultExceptionClassifier() {
  
  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      // JOOQ-specific retryable exceptions
      is DataChangedException -> true
      is DataAccessException -> {
        // Check if the underlying cause is retryable
        val cause = th.cause
        if (cause is SQLException) {
          isMessageRetryable(cause) || isCauseRetryable(th)
        } else {
          // For DataAccessException without SQLException cause, always retry to match original behavior
          true
        }
      }
      // Fall back to default classification
      else -> super.isRetryable(th)
    }
  }
}
