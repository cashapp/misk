package misk.hibernate

import misk.jdbc.DataSourceType
import misk.jdbc.retry.DefaultExceptionClassifier
import javax.persistence.OptimisticLockException
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.LockAcquisitionException

/**
 * Exception classifier for Hibernate transactions.
 *
 * Extends [DefaultExceptionClassifier] to add Hibernate-specific retryable exceptions:
 * - [StaleObjectStateException]: Hibernate optimistic locking failure
 * - [LockAcquisitionException]: Database lock acquisition failure
 * - [OptimisticLockException]: JPA optimistic locking failure
 */
class HibernateExceptionClassifier @JvmOverloads constructor(
  dataSourceType: DataSourceType? = null
) : DefaultExceptionClassifier(dataSourceType) {

  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is StaleObjectStateException,
      is LockAcquisitionException,
      is OptimisticLockException -> true
      else -> super.isRetryable(th)
    }
  }
}
