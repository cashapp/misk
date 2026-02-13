package misk.hibernate

import misk.jdbc.retry.DefaultExceptionClassifier
import misk.jdbc.retry.RetryTransactionException
import javax.persistence.OptimisticLockException
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.LockAcquisitionException

/**
 * Exception classifier for Hibernate-specific exceptions.
 * 
 * This extends the default classifier to handle Hibernate-specific retryable exceptions
 * like OptimisticLockException, StaleObjectStateException, etc.
 */
class HibernateExceptionClassifier : DefaultExceptionClassifier() {
  
  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      // Hibernate-specific retryable exceptions
      is OptimisticLockException,
      is StaleObjectStateException,
      is LockAcquisitionException -> true
      // Custom retry exception for application-level retries
      is RetryTransactionException -> true
      // Fall back to default classification
      else -> super.isRetryable(th)
    }
  }
}
