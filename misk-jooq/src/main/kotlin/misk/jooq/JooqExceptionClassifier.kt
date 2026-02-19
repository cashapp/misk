package misk.jooq

import misk.jdbc.DataSourceType
import misk.jdbc.retry.DefaultExceptionClassifier
import org.jooq.exception.DataAccessException

/**
 * Exception classifier for JOOQ transactions.
 *
 * Extends [DefaultExceptionClassifier] to add JOOQ-specific retryable exceptions:
 * - [DataAccessException]: JOOQ's wrapper for database exceptions
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
