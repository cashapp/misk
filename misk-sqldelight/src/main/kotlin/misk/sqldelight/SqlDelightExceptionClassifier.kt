package misk.sqldelight

import app.cash.sqldelight.db.OptimisticLockException
import misk.jdbc.DataSourceType
import misk.jdbc.retry.DefaultExceptionClassifier

/**
 * Exception classifier for SQLDelight transactions.
 *
 * Extends [DefaultExceptionClassifier] to add SQLDelight-specific retryable exceptions:
 * - [OptimisticLockException]: SQLDelight's optimistic locking exception
 */
class SqlDelightExceptionClassifier
@JvmOverloads
constructor(
  dataSourceType: DataSourceType? = null,
) : DefaultExceptionClassifier(dataSourceType) {

  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is OptimisticLockException -> true
      else -> super.isRetryable(th)
    }
  }
}
