package misk.jooq

import misk.jdbc.DataSourceType
import misk.jdbc.retry.DefaultExceptionClassifier
import org.jooq.exception.DataChangedException

/**
 * Exception classifier for jOOQ transactions.
 *
 * Extends [DefaultExceptionClassifier] to add jOOQ-specific retryable exceptions:
 * - [DataChangedException]: jOOQ's optimistic-locking conflict raised by
 *   `UpdatableRecord.store()`/`refresh()` when the underlying row has changed. Retrying re-reads
 *   the latest state and re-applies the change.
 *
 * Other [org.jooq.exception.DataAccessException] subclasses are intentionally **not** retried as a
 * class — they are deterministic failures (constraint violations, type/mapping errors, no-data /
 * too-many-rows, dialect mismatches, etc.) and will not change on retry.
 *
 * A wrapping [org.jooq.exception.DataAccessException] is still retryable when its underlying cause
 * is retryable (e.g. a [java.sql.SQLRecoverableException] or a Vitess "transaction not found"
 * SQLException). The base classifier's cause-walking logic handles that path.
 */
internal class JooqExceptionClassifier(
  dataSourceType: DataSourceType? = null
) : DefaultExceptionClassifier(dataSourceType) {

  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is DataChangedException -> true
      else -> super.isRetryable(th)
    }
  }
}
