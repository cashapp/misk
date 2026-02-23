package misk.jooq

import misk.jdbc.DataSourceType
import misk.jdbc.retry.DefaultExceptionClassifier
import org.jooq.exception.DataAccessException

/**
 * Exception classifier for jOOQ transactions.
 *
 * Extends [DefaultExceptionClassifier] to add jOOQ-specific retryable exceptions:
 * - [DataAccessException]: jOOQ's wrapper for database exceptions
 */
internal class JooqExceptionClassifier(
  dataSourceType: DataSourceType? = null
) : DefaultExceptionClassifier(dataSourceType) {

  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is DataAccessException -> true
      else -> super.isRetryable(th)
    }
  }
}
